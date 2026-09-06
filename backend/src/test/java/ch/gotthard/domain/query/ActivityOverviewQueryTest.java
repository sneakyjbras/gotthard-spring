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
 * The activity screen's two statements, against the same hand-arranged fixture.
 *
 * <p>Five transactions in the window and one outside it. The channel volumes below are the converted
 * figures worked out by hand — 0.1 BTC is 5 500 francs and 1 000 euros is 940, at the indicative
 * rates — because a screen that adds bitcoin to euros and prints the result is worse than one that
 * prints nothing.
 */
class ActivityOverviewQueryTest extends AbstractQueryTest {

    private static final OffsetDateTime ANCHOR = OffsetDateTime.parse("2026-03-15T12:00:00Z");

    private ActivityOverviewQuery query;

    @BeforeEach
    void createQuery() {
        query = new ActivityOverviewQuery(jdbc);
    }

    /**
     * By hand: two card authorisations of 20 and 30 make 50; a 1 000 euro transfer at 0.94 and a
     * failed 500 franc transfer make 1 440 across two payments, one of them unsuccessful; 0.1 BTC at
     * 55 000 makes 5 500. The 9 999 forty days ago is outside the window and appears nowhere.
     */
    @Test
    void given_activityOnEveryChannel_when_findChannelBreakdown_then_volumesAreConvertedAndGroupedByChannel() {
        final UUID customer = seedThreeChannels();

        final List<ChannelActivityRow> channels =
                query.findChannelBreakdown(customer, ANCHOR.minusDays(30), ANCHOR, ReportingRates.conversion());

        assertThat(channels).extracting(ChannelActivityRow::activityType).containsExactly("CARD", "CRYPTO", "PAYMENT");
        assertChannel(channels, "CARD", 2, "50.00", 0);
        assertChannel(channels, "CRYPTO", 1, "5500.00", 0);
        assertChannel(channels, "PAYMENT", 2, "1440.00", 1);
    }

    /** A channel the customer never used is absent rather than present with noughts in it. */
    @Test
    void given_onlyCardActivity_when_findChannelBreakdown_then_theSilentChannelsAreAbsent() {
        final UUID customer = fixtures.customer("CH");
        fixtures.card(customer, ANCHOR.minusDays(1), "20.00", "Alpha", "5411", true, null);

        final List<ChannelActivityRow> channels =
                query.findChannelBreakdown(customer, ANCHOR.minusDays(30), ANCHOR, ReportingRates.conversion());

        assertThat(channels).extracting(ChannelActivityRow::activityType).containsExactly("CARD");
    }

    /** The first and last activity on a channel bound what an operator is looking at. */
    @Test
    void given_twoCardTransactions_when_findChannelBreakdown_then_theChannelCarriesItsFirstAndLastInstant() {
        final UUID customer = fixtures.customer("CH");
        fixtures.card(customer, ANCHOR.minusDays(5), "20.00", "Alpha", "5411", true, null);
        fixtures.card(customer, ANCHOR.minusDays(1), "30.00", "Beta", "5411", true, null);

        final ChannelActivityRow card = query.findChannelBreakdown(
                        customer, ANCHOR.minusDays(30), ANCHOR, ReportingRates.conversion())
                .getFirst();

        assertThat(card.firstAt().toInstant()).isEqualTo(ANCHOR.minusDays(5).toInstant());
        assertThat(card.lastAt().toInstant()).isEqualTo(ANCHOR.minusDays(1).toInstant());
    }

    /**
     * Newest first, window respected, and each channel identified by what an operator would look for
     * — the merchant and its category, the beneficiary and its bank's country, the receiving wallet
     * and its chain.
     */
    @Test
    void given_activityOnEveryChannel_when_findRecentActivity_then_theNewestComeBackWithTheirCounterparties() {
        final UUID customer = seedThreeChannels();

        final List<RecentActivityRow> recent = query.findRecentActivity(customer, ANCHOR.minusDays(30), ANCHOR, 25);

        assertThat(recent).hasSize(5);
        assertThat(recent.getFirst().activityType()).isEqualTo("CARD");
        assertThat(recent.getFirst().counterparty()).isEqualTo("Beta");
        assertThat(recent.getFirst().channelDetail()).isEqualTo("5411");
        assertThat(recent).extracting(RecentActivityRow::amount).doesNotContain(new BigDecimal("9999.00"));
        assertThat(byChannel(recent, "PAYMENT").channelDetail()).isEqualTo("DE");
        assertThat(byChannel(recent, "CRYPTO").channelDetail()).isEqualTo("BITCOIN");
        assertThat(byChannel(recent, "CRYPTO").counterparty()).isEqualTo("aoq-W2");
    }

    /** Amounts on this list are what the customer was charged, not a reporting-currency restatement. */
    @Test
    void given_activityInForeignCurrency_when_findRecentActivity_then_theOriginalAmountIsKept() {
        final UUID customer = fixtures.customer("CH");
        fixtures.payment(customer, ANCHOR.minusDays(2), "1000.00", "EUR", "ACC-E", "DE");

        final RecentActivityRow payment = query.findRecentActivity(customer, ANCHOR.minusDays(30), ANCHOR, 25)
                .getFirst();

        assertThat(payment.amount()).isEqualByComparingTo("1000.00");
        assertThat(payment.currency()).isEqualTo("EUR");
    }

    /** The page size is a limit, not a suggestion. */
    @Test
    void given_moreActivityThanTheLimit_when_findRecentActivity_then_onlyTheNewestComeBack() {
        final UUID customer = seedThreeChannels();

        final List<RecentActivityRow> recent = query.findRecentActivity(customer, ANCHOR.minusDays(30), ANCHOR, 2);

        assertThat(recent).hasSize(2);
        assertThat(recent).extracting(RecentActivityRow::activityType).containsExactly("CARD", "PAYMENT");
    }

    /**
     * Card at −5d (20) and −1d (30); a 1 000 EUR payment at −2d; a failed 500 CHF payment at −3d; 0.1
     * BTC at −4d; and one transaction forty days ago that no window under test reaches.
     */
    private UUID seedThreeChannels() {
        final UUID customer = fixtures.customer("CH");
        fixtures.card(customer, ANCHOR.minusDays(5), "20.00", "Alpha", "5411", true, null);
        fixtures.card(customer, ANCHOR.minusDays(1), "30.00", "Beta", "5411", true, null);
        fixtures.payment(customer, ANCHOR.minusDays(2), "1000.00", "EUR", "ACC-E", "DE");
        fixtures.paymentWithStatus(customer, ANCHOR.minusDays(3), "500.00", "FAILED");
        fixtures.crypto(customer, ANCHOR.minusDays(4), "0.10", "BTC", "aoq-W1", "aoq-W2", null);
        fixtures.payment(customer, ANCHOR.minusDays(40), "9999.00", "ACC-OLD", "CH");
        return customer;
    }

    private static void assertChannel(
            final List<ChannelActivityRow> channels,
            final String activityType,
            final long transactionCount,
            final String volume,
            final long failedCount) {
        final ChannelActivityRow channel = channels.stream()
                .filter(row -> row.activityType().equals(activityType))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + activityType + " row"));
        assertThat(channel.transactionCount()).as("%s count", activityType).isEqualTo(transactionCount);
        assertThat(channel.volume()).as("%s volume", activityType).isEqualByComparingTo(volume);
        assertThat(channel.failedCount()).as("%s failures", activityType).isEqualTo(failedCount);
    }

    private static RecentActivityRow byChannel(final List<RecentActivityRow> recent, final String activityType) {
        return recent.stream()
                .filter(row -> row.activityType().equals(activityType))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + activityType + " row"));
    }
}
