package ch.gotthard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.CryptoSignals;
import ch.gotthard.core.model.Features;
import ch.gotthard.domain.query.ActivityRow;
import ch.gotthard.domain.query.CardRow;
import ch.gotthard.domain.query.CryptoRow;
import ch.gotthard.domain.query.FeatureRow;
import ch.gotthard.domain.query.PaymentRow;
import ch.gotthard.domain.query.VelocityRow;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The seam, tested on its own: rows in, {@link Features} out, no database and no container.
 *
 * <p>These are the conversions that are easy to get quietly wrong — a null that should become
 * "forever" rather than "just now", a blank-padded country code the core will reject, a channel's
 * signals landing on the wrong record — and each of them is one assertion here.
 */
class FeatureAssemblerTest {

    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.parse("2026-03-15T12:00:00Z");
    private static final UUID TRANSACTION_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    private final FeatureAssembler assembler = new FeatureAssembler();

    @Test
    void given_aCardRow_when_assemble_then_theCardSignalsArriveAndTheOtherChannelsAreEmpty() {
        final Features features =
                assembler.assemble(row("CARD", card(3, 2, 4, "2500.00"), emptyPayment(), emptyCrypto()));

        assertThat(features.activityType()).isEqualTo(ActivityType.CARD);
        assertThat(features.card().declineCount1h()).isEqualTo(3);
        assertThat(features.card().cardNotPresentDeclineCount1h()).isEqualTo(2);
        assertThat(features.card().distinctMerchantCount1h()).isEqualTo(4);
        assertThat(features.card().quasiCashVolume24h().amount()).isEqualByComparingTo("2500.00");
        assertThat(features.payment().crossBorderCount7d()).isZero();
        assertThat(features.crypto().outboundVolume24h().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void given_aPaymentRow_when_assemble_then_theCorridorArrivesAndTheOtherChannelsAreEmpty() {
        final FeatureRow row = row(
                "PAYMENT",
                emptyCard(),
                new PaymentRow(List.of("DE", "IR", "IR"), 3, new BigDecimal("7000.00")),
                emptyCrypto());

        final Features features = assembler.assemble(row);

        assertThat(features.activityType()).isEqualTo(ActivityType.PAYMENT);
        assertThat(features.payment().counterpartyCountries7d()).containsExactlyInAnyOrder("DE", "IR");
        assertThat(features.payment().crossBorderCount7d()).isEqualTo(3);
        assertThat(features.payment().crossBorderVolume7d().amount()).isEqualByComparingTo("7000.00");
        assertThat(features.card().declineCount1h()).isZero();
    }

    /** {@code CHAR(2)} arrives blank-padded from PostgreSQL, and the core rejects a three-character country. */
    @Test
    void given_blankPaddedCountryCodes_when_assemble_then_theyAreTrimmedToTwoCharacters() {
        final FeatureRow row = row(
                "PAYMENT",
                emptyCard(),
                new PaymentRow(List.of("DE ", " IR"), 2, new BigDecimal("10.00")),
                emptyCrypto());

        assertThat(assembler.assemble(row).payment().counterpartyCountries7d()).containsExactlyInAnyOrder("DE", "IR");
    }

    @Test
    void given_aCryptoRowWithFunding_when_assemble_then_theGapBecomesADuration() {
        final FeatureRow row = row(
                "CRYPTO", emptyCard(), emptyPayment(), new CryptoRow(true, 1_800L, new BigDecimal("11000.00"), "W1"));

        final CryptoSignals crypto = assembler.assemble(row).crypto();

        assertThat(crypto.destinationIsExchange()).isTrue();
        assertThat(crypto.sinceInboundFunding()).isEqualTo(Duration.ofMinutes(30));
        assertThat(crypto.outboundVolume24h().amount()).isEqualByComparingTo("11000.00");
    }

    /**
     * Funding nobody ever saw is not funding that happened a moment ago. Mapping the null to nought
     * would make every unfunded wallet look like a pass-through and fire the rapid-disposal rule on
     * all of them.
     */
    @Test
    void given_aCryptoRowWithNoFunding_when_assemble_then_theGapIsForeverRatherThanNought() {
        final FeatureRow row =
                row("CRYPTO", emptyCard(), emptyPayment(), new CryptoRow(false, null, new BigDecimal("250.00"), "W5"));

        assertThat(assembler.assemble(row).crypto().sinceInboundFunding()).isEqualTo(CryptoSignals.NO_INBOUND_FUNDING);
    }

    /** With no graph search wired in, every wallet is honestly unreachable rather than optimistically clean. */
    @Test
    void given_noWalletGraph_when_assemble_then_theFlaggedWalletDistanceIsUnknown() {
        final FeatureRow row =
                row("CRYPTO", emptyCard(), emptyPayment(), new CryptoRow(false, null, new BigDecimal("1.00"), "W5"));

        assertThat(assembler.assemble(row).crypto().hopsToFlaggedWallet())
                .isEqualTo(CryptoSignals.NO_PATH_TO_FLAGGED_WALLET);
    }

    @Test
    void given_aWalletProximity_when_assemble_then_itsAnswerReachesTheCryptoSignals() {
        final FeatureRow row =
                row("CRYPTO", emptyCard(), emptyPayment(), new CryptoRow(false, null, new BigDecimal("1.00"), "W5"));

        final Features features = assembler.assemble(row, wallet -> wallet.equals("W5") ? 2 : 9);

        assertThat(features.crypto().hopsToFlaggedWallet()).isEqualTo(2);
    }

    /** Every monetary signal on one {@code Features} shares the transaction's reporting currency. */
    @Test
    void given_anyRow_when_assemble_then_velocityIsStatedInTheReportingCurrency() {
        final Features features = assembler.assemble(row("CARD", emptyCard(), emptyPayment(), emptyCrypto()));

        assertThat(features.velocity().rollingVolume24h().currency()).isEqualTo("CHF");
        assertThat(features.velocity().rollingVolume24h().amount()).isEqualByComparingTo("5600.00");
        assertThat(features.velocity().rollingVolume7d().amount()).isEqualByComparingTo("6200.00");
        assertThat(features.velocity().transactionCount24h()).isEqualTo(3);
        assertThat(features.velocity().nearThresholdCount7d()).isEqualTo(1);
        assertThat(features.velocity().dormancyBeforeActivity()).isEqualTo(Duration.ofHours(1));
    }

    /** A channel the schema does not have is a bug in the query, and it fails loudly rather than scoring. */
    @Test
    void given_anUnknownActivityType_when_assemble_then_itIsRefused() {
        assertThatThrownBy(() -> assembler.assemble(row("WIRE", emptyCard(), emptyPayment(), emptyCrypto())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static FeatureRow row(
            final String activityType, final CardRow card, final PaymentRow payment, final CryptoRow crypto) {
        return new FeatureRow(
                new ActivityRow(
                        TRANSACTION_ID, CUSTOMER_ID, activityType, OCCURRED_AT, new BigDecimal("3200.00"), "CHF"),
                new VelocityRow(new BigDecimal("5600.00"), new BigDecimal("6200.00"), 3, 1, 3_600L),
                card,
                payment,
                crypto);
    }

    private static CardRow card(
            final long declines, final long cardNotPresent, final long merchants, final String quasiCash) {
        return new CardRow(declines, cardNotPresent, merchants, new BigDecimal(quasiCash));
    }

    private static CardRow emptyCard() {
        return new CardRow(0, 0, 0, BigDecimal.ZERO);
    }

    private static PaymentRow emptyPayment() {
        return new PaymentRow(List.of(), 0, BigDecimal.ZERO);
    }

    private static CryptoRow emptyCrypto() {
        return new CryptoRow(false, null, BigDecimal.ZERO, null);
    }
}
