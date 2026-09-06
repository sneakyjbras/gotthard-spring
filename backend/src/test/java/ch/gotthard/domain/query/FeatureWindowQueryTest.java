package ch.gotthard.domain.query;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.service.ReportingRates;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What the window frames actually contain.
 *
 * <p>Every expected number below was worked out by hand from the fixture times before the query was
 * run, and is written here as a literal. That is the only way this test is worth anything: reading
 * the query's own output back and asserting it equals itself would pass just as happily with {@code
 * ROWS} in place of {@code RANGE}, or with a frame an hour too wide, and those are exactly the
 * mistakes that would silently poison every risk score downstream.
 *
 * <p>The fixture times are chosen so several rows land <em>exactly</em> on a frame boundary — a
 * transaction precisely twenty-four hours old, another precisely seven days old — because inclusive
 * is what {@code PRECEDING} means and an off-by-one there is invisible in ordinary data.
 */
class FeatureWindowQueryTest extends AbstractQueryTest {

    /** A fixed instant, so every "hours before" in the comments below is arithmetic, not a guess. */
    private static final OffsetDateTime ANCHOR = OffsetDateTime.parse("2026-03-15T12:00:00Z");

    /** 9 000 up to 10 000 — the band {@link NearThresholdBand#below} derives from the reporting threshold. */
    private static final NearThresholdBand BAND = NearThresholdBand.below(new BigDecimal("10000.00"));

    private static final CurrencyConversion NO_CONVERSION = CurrencyConversion.identity("CHF");

    private FeatureWindowQuery query;

    @BeforeEach
    void createQuery() {
        query = new FeatureWindowQuery(jdbc);
    }

    /**
     * Six payments at −192h, −168h, −25h, −24h, −1h and 0h, of 100, 200, 400, 800, 1 600 and 3 200.
     *
     * <p>Worked out by hand, frame by frame. The rows that make the assertion mean something are
     * −25h and −24h: they are adjacent in row order but twenty-five hours apart in value, so a
     * positional frame and a value frame give different answers, and only one of them is written
     * below.
     *
     * <ul>
     *   <li>−192h: 24h frame [−216, −192] holds itself → 100. 7d frame [−360, −192] → 100.
     *   <li>−168h: [−192, −168] holds −192h <b>on the boundary</b> → 300. 7d [−336, −168] → 300.
     *   <li>−25h: [−49, −25] holds itself alone → 400. 7d [−193, −25] holds −192h and −168h → 700.
     *   <li>−24h: [−48, −24] holds −25h → 1 200. 7d [−192, −24] holds −192h <b>on the boundary</b>
     *       → 1 500.
     *   <li>−1h: [−25, −1] holds −25h <b>on the boundary</b> and −24h → 2 800. 7d [−169, −1] holds
     *       −168h but not −192h → 3 000.
     *   <li>0h: [−24, 0] holds −24h <b>on the boundary</b> and −1h → 5 600. 7d [−168, 0] holds
     *       −168h <b>on the boundary</b> → 6 200.
     * </ul>
     */
    @Test
    void given_transactionsSpreadOverEightDays_when_findFeatures_then_rollingSumsCoverATimeRangeNotARowCount() {
        final UUID customer = fixtures.customer("CH");
        final UUID at192hAgo = fixtures.payment(customer, ANCHOR.minusHours(192), "100.00", "ACC-1", "CH");
        final UUID at168hAgo = fixtures.payment(customer, ANCHOR.minusHours(168), "200.00", "ACC-2", "CH");
        final UUID at25hAgo = fixtures.payment(customer, ANCHOR.minusHours(25), "400.00", "ACC-3", "CH");
        final UUID at24hAgo = fixtures.payment(customer, ANCHOR.minusHours(24), "800.00", "ACC-4", "CH");
        final UUID at1hAgo = fixtures.payment(customer, ANCHOR.minusHours(1), "1600.00", "ACC-5", "CH");
        final UUID atAnchor = fixtures.payment(customer, ANCHOR, "3200.00", "ACC-6", "CH");

        final List<FeatureRow> rows = features(customer, ANCHOR.minusDays(10), ANCHOR);

        assertThat(rows).hasSize(6);
        assertRollingVolumes(rows, at192hAgo, "100.00", "100.00");
        assertRollingVolumes(rows, at168hAgo, "300.00", "300.00");
        assertRollingVolumes(rows, at25hAgo, "400.00", "700.00");
        assertRollingVolumes(rows, at24hAgo, "1200.00", "1500.00");
        assertRollingVolumes(rows, at1hAgo, "2800.00", "3000.00");
        assertRollingVolumes(rows, atAnchor, "5600.00", "6200.00");
    }

    /** Counts share the frame with the sums: 1, 2, 1, 2, 3, 3 for the same six rows. */
    @Test
    void given_transactionsSpreadOverEightDays_when_findFeatures_then_theDayCountFollowsTheSameFrame() {
        final UUID customer = fixtures.customer("CH");
        final UUID at192hAgo = fixtures.payment(customer, ANCHOR.minusHours(192), "100.00", "ACC-1", "CH");
        final UUID at168hAgo = fixtures.payment(customer, ANCHOR.minusHours(168), "200.00", "ACC-2", "CH");
        final UUID at25hAgo = fixtures.payment(customer, ANCHOR.minusHours(25), "400.00", "ACC-3", "CH");
        final UUID at24hAgo = fixtures.payment(customer, ANCHOR.minusHours(24), "800.00", "ACC-4", "CH");
        final UUID at1hAgo = fixtures.payment(customer, ANCHOR.minusHours(1), "1600.00", "ACC-5", "CH");
        final UUID atAnchor = fixtures.payment(customer, ANCHOR, "3200.00", "ACC-6", "CH");

        final List<FeatureRow> rows = features(customer, ANCHOR.minusDays(10), ANCHOR);

        assertThat(count24h(rows, at192hAgo)).isEqualTo(1);
        assertThat(count24h(rows, at168hAgo)).isEqualTo(2);
        assertThat(count24h(rows, at25hAgo)).isEqualTo(1);
        assertThat(count24h(rows, at24hAgo)).isEqualTo(2);
        assertThat(count24h(rows, at1hAgo)).isEqualTo(3);
        assertThat(count24h(rows, atAnchor)).isEqualTo(3);
    }

    /**
     * Gaps to the previous activity: none, 24h, 143h, 1h, 23h, 1h — in seconds, 0, 86 400, 514 800,
     * 3 600, 82 800, 3 600.
     */
    @Test
    void given_aCustomerWithHistory_when_findFeatures_then_dormancyIsTheGapToThePreviousActivity() {
        final UUID customer = fixtures.customer("CH");
        final UUID at192hAgo = fixtures.payment(customer, ANCHOR.minusHours(192), "100.00", "ACC-1", "CH");
        final UUID at168hAgo = fixtures.payment(customer, ANCHOR.minusHours(168), "200.00", "ACC-2", "CH");
        final UUID at25hAgo = fixtures.payment(customer, ANCHOR.minusHours(25), "400.00", "ACC-3", "CH");
        final UUID at24hAgo = fixtures.payment(customer, ANCHOR.minusHours(24), "800.00", "ACC-4", "CH");
        final UUID atAnchor = fixtures.payment(customer, ANCHOR, "3200.00", "ACC-6", "CH");

        final List<FeatureRow> rows = features(customer, ANCHOR.minusDays(10), ANCHOR);

        assertThat(row(rows, at192hAgo).velocity().dormancySeconds()).isZero();
        assertThat(row(rows, at168hAgo).velocity().dormancySeconds()).isEqualTo(86_400L);
        assertThat(row(rows, at25hAgo).velocity().dormancySeconds()).isEqualTo(514_800L);
        assertThat(row(rows, at24hAgo).velocity().dormancySeconds()).isEqualTo(3_600L);
        assertThat(row(rows, atAnchor).velocity().dormancySeconds()).isEqualTo(86_400L);
    }

    /**
     * Three payments, two of them sharing an instant: 50 at −2h, then 500 and 700 together at 0h.
     *
     * <p>Rows with equal {@code created_at} are peers, and RANGE puts every peer in every peer's
     * frame. Both of the two therefore see 50 + 500 + 700 = 1 250 and a count of three. Under ROWS
     * whichever peer the sort placed first would see 550, and which one that was would vary between
     * runs — a risk score that is not a function of the data.
     */
    @Test
    void given_transactionsAtTheSameInstant_when_findFeatures_then_eachPeerSeesTheOther() {
        final UUID customer = fixtures.customer("CH");
        fixtures.payment(customer, ANCHOR.minusHours(2), "50.00", "ACC-1", "CH");
        final UUID firstPeer = fixtures.payment(customer, ANCHOR, "500.00", "ACC-2", "CH");
        final UUID secondPeer = fixtures.payment(customer, ANCHOR, "700.00", "ACC-3", "CH");

        final List<FeatureRow> rows = features(customer, ANCHOR.minusDays(1), ANCHOR);

        assertThat(row(rows, firstPeer).velocity().rollingVolume24h()).isEqualByComparingTo("1250.00");
        assertThat(row(rows, secondPeer).velocity().rollingVolume24h()).isEqualByComparingTo("1250.00");
        assertThat(count24h(rows, firstPeer)).isEqualTo(3);
        assertThat(count24h(rows, secondPeer)).isEqualTo(3);
    }

    /** The frame is partitioned by customer, so another customer's 9 999 is not in anybody else's day. */
    @Test
    void given_anotherCustomersActivity_when_findFeatures_then_itIsOutsideThePartition() {
        final UUID customer = fixtures.customer("CH");
        final UUID otherCustomer = fixtures.customer("CH");
        final UUID mine = fixtures.payment(customer, ANCHOR, "100.00", "ACC-1", "CH");
        fixtures.payment(otherCustomer, ANCHOR.minusHours(1), "9999.00", "ACC-2", "CH");

        final List<FeatureRow> rows = features(customer, ANCHOR.minusDays(1), ANCHOR);

        assertThat(rows).hasSize(1);
        assertThat(row(rows, mine).velocity().rollingVolume24h()).isEqualByComparingTo("100.00");
        assertThat(count24h(rows, mine)).isEqualTo(1);
    }

    /**
     * History outside the reported window still shapes the aggregates inside it.
     *
     * <p>Asked only for the last twelve hours, the query returns two rows — and the later one's
     * seven-day sum is still 6 200, because the frames are computed over the whole timeline and the
     * window is applied afterwards. Filtering first would leave the earliest reported transaction
     * with a seven-day sum containing one row.
     */
    @Test
    void given_activityBeforeTheReportedWindow_when_findFeatures_then_itCountsWithoutBeingReturned() {
        final UUID customer = fixtures.customer("CH");
        fixtures.payment(customer, ANCHOR.minusHours(192), "100.00", "ACC-1", "CH");
        fixtures.payment(customer, ANCHOR.minusHours(168), "200.00", "ACC-2", "CH");
        fixtures.payment(customer, ANCHOR.minusHours(25), "400.00", "ACC-3", "CH");
        fixtures.payment(customer, ANCHOR.minusHours(24), "800.00", "ACC-4", "CH");
        final UUID at1hAgo = fixtures.payment(customer, ANCHOR.minusHours(1), "1600.00", "ACC-5", "CH");
        final UUID atAnchor = fixtures.payment(customer, ANCHOR, "3200.00", "ACC-6", "CH");

        final List<FeatureRow> rows = features(customer, ANCHOR.minusHours(12), ANCHOR);

        assertThat(rows).hasSize(2);
        assertRollingVolumes(rows, at1hAgo, "2800.00", "3000.00");
        assertRollingVolumes(rows, atAnchor, "5600.00", "6200.00");
    }

    /**
     * Six payments in a week: 9 500, 9 600, 12 000, 9 700 and 8 000 to one beneficiary, 9 800 to
     * another. The band is 9 000 up to 10 000, so 12 000 and 8 000 are outside it.
     *
     * <p>By hand, per row, over the seven days behind it and within its own beneficiary's partition:
     * 1, 2, 2, 3, 1, 3. The 9 800 scores 1 rather than 4 because it went somewhere else — which is
     * the whole point of partitioning by beneficiary. Spread across unrelated counterparties the
     * same six amounts are a week of ordinary business.
     */
    @Test
    void given_nearThresholdPaymentsToTwoBeneficiaries_when_findFeatures_then_eachBeneficiaryIsCountedApart() {
        final UUID customer = fixtures.customer("CH");
        final UUID first = fixtures.payment(customer, ANCHOR.minusDays(6), "9500.00", "ACC-A", "CH");
        final UUID second = fixtures.payment(customer, ANCHOR.minusDays(4), "9600.00", "ACC-A", "CH");
        final UUID overTheThreshold = fixtures.payment(customer, ANCHOR.minusDays(3), "12000.00", "ACC-A", "CH");
        final UUID third = fixtures.payment(customer, ANCHOR.minusDays(2), "9700.00", "ACC-A", "CH");
        final UUID otherBeneficiary = fixtures.payment(customer, ANCHOR.minusDays(1), "9800.00", "ACC-B", "CH");
        final UUID belowTheBand = fixtures.payment(customer, ANCHOR, "8000.00", "ACC-A", "CH");

        final List<FeatureRow> rows = features(customer, ANCHOR.minusDays(10), ANCHOR);

        assertThat(nearThreshold(rows, first)).isEqualTo(1);
        assertThat(nearThreshold(rows, second)).isEqualTo(2);
        assertThat(nearThreshold(rows, overTheThreshold)).isEqualTo(2);
        assertThat(nearThreshold(rows, third)).isEqualTo(3);
        assertThat(nearThreshold(rows, otherBeneficiary)).isEqualTo(1);
        assertThat(nearThreshold(rows, belowTheBand)).isEqualTo(3);
        assertThat(row(rows, belowTheBand).velocity().rollingVolume7d()).isEqualByComparingTo("58600.00");
    }

    /**
     * Five card authorisations in the last hour and a half: a clean one at −90m, three
     * card-not-present declines at −50m, −30m and −10m across two merchants, and a clean cash
     * withdrawal at 0.
     *
     * <p>By hand: the −90m row's hour holds only itself, so no declines and one merchant. The −30m
     * row's hour [−90m, −30m] holds the −90m row <b>on the boundary</b>, so two declines across
     * three merchants. The −10m row's hour [−70m, −10m] holds the three declines but not the −90m
     * row, so three declines across two merchants. The 0h row's hour holds all three declines and
     * three merchants.
     */
    @Test
    void given_cardDeclinesWithinTheHour_when_findFeatures_then_declineAndMerchantCountsShareTheFrame() {
        final UUID customer = fixtures.customer("CH");
        final UUID clean = fixtures.card(customer, ANCHOR.minusMinutes(90), "20.00", "Alpha", "5411", true, null);
        fixtures.card(customer, ANCHOR.minusMinutes(50), "10.00", "Beta", "5732", false, "DO_NOT_HONOR");
        final UUID middleDecline =
                fixtures.card(customer, ANCHOR.minusMinutes(30), "12.00", "Gamma", "5732", false, "DO_NOT_HONOR");
        final UUID lastDecline =
                fixtures.card(customer, ANCHOR.minusMinutes(10), "15.00", "Beta", "5732", false, "DO_NOT_HONOR");
        final UUID cashWithdrawal = fixtures.card(customer, ANCHOR, "3000.00", "Delta", "6011", true, null);

        final List<FeatureRow> rows = features(customer, ANCHOR.minusDays(1), ANCHOR);

        assertCardCounts(rows, clean, 0, 0, 1);
        assertCardCounts(rows, middleDecline, 2, 2, 3);
        assertCardCounts(rows, lastDecline, 3, 3, 2);
        assertCardCounts(rows, cashWithdrawal, 3, 3, 3);
    }

    /**
     * Only the quasi-cash categories are summed: 3 000 at MCC 6011 (a cash machine) counts, 20 at
     * 5411 (a grocer) and the three declines at 5732 do not.
     */
    @Test
    void given_cardSpendInSeveralCategories_when_findFeatures_then_onlyQuasiCashMerchantsAreSummed() {
        final UUID customer = fixtures.customer("CH");
        final UUID grocer = fixtures.card(customer, ANCHOR.minusMinutes(90), "20.00", "Alpha", "5411", true, null);
        final UUID cashWithdrawal = fixtures.card(customer, ANCHOR, "3000.00", "Delta", "6011", true, null);

        final List<FeatureRow> rows = features(customer, ANCHOR.minusDays(1), ANCHOR);

        assertThat(row(rows, grocer).card().quasiCashVolume24h()).isEqualByComparingTo("0.00");
        assertThat(row(rows, cashWithdrawal).card().quasiCashVolume24h()).isEqualByComparingTo("3000.00");
    }

    /** A card row carries no payment corridor and no wallet history — the other channels are empty, not absent. */
    @Test
    void given_aCardTransaction_when_findFeatures_then_theOtherChannelsAreEmpty() {
        final UUID customer = fixtures.customer("CH");
        final UUID card = fixtures.card(customer, ANCHOR, "20.00", "Alpha", "5411", true, null);

        final FeatureRow row = row(features(customer, ANCHOR.minusDays(1), ANCHOR), card);

        assertThat(row.payment().counterpartyCountries7d()).isEmpty();
        assertThat(row.payment().crossBorderCount7d()).isZero();
        assertThat(row.payment().crossBorderVolume7d()).isEqualByComparingTo("0.00");
        assertThat(row.crypto().destinationIsExchange()).isFalse();
        assertThat(row.crypto().sinceInboundFundingSeconds()).isNull();
        assertThat(row.crypto().outboundVolume24h()).isEqualByComparingTo("0.00");
    }

    /**
     * A Swiss customer's five payments: 5 000 to FR at −9d, 1 000 to DE at −6d, 2 000 to IR at −5d,
     * 3 000 domestic at −3d, 4 000 to IR at 0.
     *
     * <p>By hand for the last one: its seven days reach back to −7d, so the FR payment is out and
     * the other four are in. Three of those four left Switzerland, coming to 1 000 + 2 000 + 4 000 =
     * 7 000. The domestic payment is in the corridor set but not in the cross-border figures — which
     * is what "cross-border" has to mean when it is measured against the customer's own country.
     */
    @Test
    void given_paymentsToSeveralCountries_when_findFeatures_then_crossBorderFiguresExcludeTheDomesticOnes() {
        final UUID customer = fixtures.customer("CH");
        final UUID toFrance = fixtures.payment(customer, ANCHOR.minusDays(9), "5000.00", "ACC-F", "FR");
        final UUID toGermany = fixtures.payment(customer, ANCHOR.minusDays(6), "1000.00", "ACC-D", "DE");
        fixtures.payment(customer, ANCHOR.minusDays(5), "2000.00", "ACC-I", "IR");
        final UUID domestic = fixtures.payment(customer, ANCHOR.minusDays(3), "3000.00", "ACC-C", "CH");
        final UUID toIran = fixtures.payment(customer, ANCHOR, "4000.00", "ACC-I2", "IR");

        final List<FeatureRow> rows = features(customer, ANCHOR.minusDays(10), ANCHOR);

        assertThat(row(rows, toIran).payment().counterpartyCountries7d())
                .containsExactlyInAnyOrder("DE", "IR", "IR", "CH");
        assertThat(row(rows, toIran).payment().crossBorderCount7d()).isEqualTo(3);
        assertThat(row(rows, toIran).payment().crossBorderVolume7d()).isEqualByComparingTo("7000.00");

        assertThat(row(rows, toGermany).payment().crossBorderCount7d()).isEqualTo(2);
        assertThat(row(rows, toGermany).payment().crossBorderVolume7d()).isEqualByComparingTo("6000.00");

        assertThat(row(rows, domestic).payment().crossBorderCount7d()).isEqualTo(3);
        assertThat(row(rows, domestic).payment().crossBorderVolume7d()).isEqualByComparingTo("8000.00");
        assertThat(row(rows, toFrance).payment().crossBorderCount7d()).isEqualTo(1);
    }

    /**
     * One wallet, funded twice by somebody else and emptied three times by this customer: 500 in at
     * −30h, 100 out at −26h, 4 000 out at −2h, 20 000 in at −30m, 7 000 out to an exchange at 0.
     *
     * <p>By hand, and note the partition is the <b>wallet</b>, not the customer — the two inbound
     * transfers belong to a different customer entirely and still count, because money arriving at a
     * wallet arrives at it whoever sent it:
     *
     * <ul>
     *   <li>−26h: day frame [−50h, −26h] holds only itself → 100 out. Last inbound at or before it
     *       is the −30h one → a four-hour gap, 14 400 seconds.
     *   <li>−2h: day frame [−26h, −2h] holds the −26h transfer <b>on the boundary</b> → 4 100 out.
     *       The 20 000 arrived later, so the last inbound is still −30h → 100 800 seconds.
     *   <li>0h: day frame [−24h, 0] excludes the −26h transfer → 11 000 out. The last inbound is now
     *       the −30m one → 1 800 seconds, and the destination is an exchange.
     * </ul>
     */
    @Test
    void given_aWalletFundedByAnotherCustomer_when_findFeatures_then_theWalletsOwnLedgerIsUsed() {
        final UUID customer = fixtures.customer("CH");
        final UUID funder = fixtures.customer("CH");
        fixtures.crypto(funder, ANCHOR.minusHours(30), "500.00", "fwq-EXT-A", "fwq-W1", null);
        fixtures.crypto(funder, ANCHOR.minusMinutes(30), "20000.00", "fwq-EXT-B", "fwq-W1", null);
        final UUID firstOut = fixtures.crypto(customer, ANCHOR.minusHours(26), "100.00", "fwq-W1", "fwq-W9", null);
        final UUID secondOut = fixtures.crypto(customer, ANCHOR.minusHours(2), "4000.00", "fwq-W1", "fwq-W2", null);
        final UUID toExchange = fixtures.crypto(customer, ANCHOR, "7000.00", "fwq-W1", "fwq-EXCH", "Kraken");

        final List<FeatureRow> rows = features(customer, ANCHOR.minusDays(10), ANCHOR);

        assertCrypto(rows, firstOut, "100.00", 14_400L, false);
        assertCrypto(rows, secondOut, "4100.00", 100_800L, false);
        assertCrypto(rows, toExchange, "11000.00", 1_800L, true);
        assertThat(row(rows, toExchange).crypto().sendingWallet()).isEqualTo("fwq-W1");
    }

    /** A wallet nobody was ever seen funding has no gap at all — null here, forever in the core. */
    @Test
    void given_aWalletWithNoInboundFunding_when_findFeatures_then_theFundingGapIsAbsent() {
        final UUID customer = fixtures.customer("CH");
        final UUID onlyOut = fixtures.crypto(customer, ANCHOR.minusHours(3), "250.00", "fwq-W5", "fwq-W6", null);

        final List<FeatureRow> rows = features(customer, ANCHOR.minusDays(1), ANCHOR);

        assertThat(row(rows, onlyOut).crypto().sinceInboundFundingSeconds()).isNull();
        assertThat(row(rows, onlyOut).crypto().outboundVolume24h()).isEqualByComparingTo("250.00");
    }

    /**
     * 0.1 BTC at −2h, 1 000 EUR at −1h and 60 CHF at 0, converted at the indicative rates: 0.1 ×
     * 55 000 = 5 500, 1 000 × 0.94 = 940, 60 × 1 = 60. The day's rolling sum is 6 500 — a number
     * that means something, unlike the 1 060.1 the raw column would have produced.
     */
    @Test
    void given_transactionsInThreeCurrencies_when_findFeatures_then_amountsAreConvertedBeforeTheyAreSummed() {
        final UUID customer = fixtures.customer("CH");
        final UUID inBitcoin = fixtures.crypto(customer, ANCHOR.minusHours(2), "0.10", "BTC", "fwq-B1", "fwq-B2", null);
        final UUID inEuros = fixtures.payment(customer, ANCHOR.minusHours(1), "1000.00", "EUR", "ACC-E", "DE");
        final UUID inFrancs = fixtures.payment(customer, ANCHOR, "60.00", "CHF", "ACC-C", "CH");

        final List<FeatureRow> rows =
                query.findFeatures(customer, ANCHOR.minusDays(1), ANCHOR, ReportingRates.conversion(), BAND);

        assertThat(row(rows, inBitcoin).activity().amount()).isEqualByComparingTo("5500.00");
        assertThat(row(rows, inEuros).activity().amount()).isEqualByComparingTo("940.00");
        assertThat(row(rows, inFrancs).activity().amount()).isEqualByComparingTo("60.00");
        assertThat(row(rows, inFrancs).activity().reportingCurrency()).isEqualTo("CHF");
        assertThat(row(rows, inFrancs).velocity().rollingVolume24h()).isEqualByComparingTo("6500.00");
    }

    /** Nothing to report on is not a failure — an unknown customer simply has no rows. */
    @Test
    void given_aCustomerWithNoActivity_when_findFeatures_then_nothingComesBack() {
        assertThat(features(fixtures.customer("CH"), ANCHOR.minusDays(30), ANCHOR))
                .isEmpty();
    }

    private List<FeatureRow> features(final UUID customerId, final OffsetDateTime from, final OffsetDateTime to) {
        return query.findFeatures(customerId, from, to, NO_CONVERSION, BAND);
    }

    private static void assertRollingVolumes(
            final List<FeatureRow> rows, final UUID transactionId, final String over24h, final String over7d) {
        assertThat(row(rows, transactionId).velocity().rollingVolume24h())
                .as("24h volume at %s", transactionId)
                .isEqualByComparingTo(over24h);
        assertThat(row(rows, transactionId).velocity().rollingVolume7d())
                .as("7d volume at %s", transactionId)
                .isEqualByComparingTo(over7d);
    }

    private static void assertCardCounts(
            final List<FeatureRow> rows,
            final UUID transactionId,
            final int declines,
            final int cardNotPresentDeclines,
            final int distinctMerchants) {
        final CardRow card = row(rows, transactionId).card();
        assertThat(card.declineCount1h()).as("declines at %s", transactionId).isEqualTo(declines);
        assertThat(card.cardNotPresentDeclineCount1h())
                .as("card-not-present declines at %s", transactionId)
                .isEqualTo(cardNotPresentDeclines);
        assertThat(card.distinctMerchantCount1h())
                .as("distinct merchants at %s", transactionId)
                .isEqualTo(distinctMerchants);
    }

    private static void assertCrypto(
            final List<FeatureRow> rows,
            final UUID transactionId,
            final String outboundVolume24h,
            final long sinceInboundFundingSeconds,
            final boolean destinationIsExchange) {
        final CryptoRow crypto = row(rows, transactionId).crypto();
        assertThat(crypto.outboundVolume24h())
                .as("day's outflow at %s", transactionId)
                .isEqualByComparingTo(outboundVolume24h);
        assertThat(crypto.sinceInboundFundingSeconds())
                .as("funding gap at %s", transactionId)
                .isEqualTo(sinceInboundFundingSeconds);
        assertThat(crypto.destinationIsExchange()).isEqualTo(destinationIsExchange);
    }

    private static long count24h(final List<FeatureRow> rows, final UUID transactionId) {
        return row(rows, transactionId).velocity().transactionCount24h();
    }

    private static long nearThreshold(final List<FeatureRow> rows, final UUID transactionId) {
        return row(rows, transactionId).velocity().nearThresholdCount7d();
    }

    private static FeatureRow row(final List<FeatureRow> rows, final UUID transactionId) {
        return rows.stream()
                .filter(row -> row.activity().transactionId().equals(transactionId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no feature row for " + transactionId));
    }
}
