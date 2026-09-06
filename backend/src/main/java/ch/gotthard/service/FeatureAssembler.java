package ch.gotthard.service;

import ch.gotthard.core.model.Activity;
import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.CardSignals;
import ch.gotthard.core.model.CryptoSignals;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.model.Money;
import ch.gotthard.core.model.PaymentSignals;
import ch.gotthard.core.model.Velocity;
import ch.gotthard.domain.query.CardRow;
import ch.gotthard.domain.query.CryptoRow;
import ch.gotthard.domain.query.FeatureRow;
import ch.gotthard.domain.query.PaymentRow;
import ch.gotthard.domain.query.VelocityRow;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The seam: a row of window aggregates becomes the {@link Features} a rule reads.
 *
 * <p>Everything below this class speaks SQL — strings for channels, epoch seconds for gaps, nulls
 * for things that never happened. Everything above it speaks the core's vocabulary — {@link
 * ActivityType}, {@link Duration}, {@link Money}, and values like {@link
 * CryptoSignals#NO_INBOUND_FUNDING} that are honest about absence without being optional. This is
 * the only place both are spoken, which is what keeps the risk core free of the database and the
 * database free of the risk model.
 *
 * <p>The conversion is deliberately mechanical. Anything that looks like a decision — what "just
 * below the threshold" means, which merchant categories are quasi-cash, what a franc is worth in
 * bitcoin — was decided before the query ran. By the time a row reaches here the arithmetic is done
 * and only the translation is left.
 */
@Component
public class FeatureAssembler {

    /** Features with no wallet graph behind them: every crypto transfer reads as unreachable. */
    public Features assemble(final FeatureRow row) {
        return assemble(row, WalletProximity.unknown());
    }

    public Features assemble(final FeatureRow row, final WalletProximity proximity) {
        final Activity activity = activityOf(row);
        final Velocity velocity = velocityOf(row.velocity(), activity.amount().currency());
        return switch (activity.type()) {
            case CARD -> Features.card(activity, velocity, cardOf(row.card(), activity));
            case PAYMENT -> Features.payment(activity, velocity, paymentOf(row.payment(), activity));
            case CRYPTO -> Features.crypto(activity, velocity, cryptoOf(row.crypto(), activity, proximity));
        };
    }

    private static Activity activityOf(final FeatureRow row) {
        return new Activity(
                row.activity().transactionId(),
                row.activity().customerId(),
                ActivityType.valueOf(row.activity().activityType()),
                Money.of(row.activity().reportingCurrency(), row.activity().amount()),
                row.activity().occurredAt().toInstant());
    }

    private static Velocity velocityOf(final VelocityRow velocity, final String currency) {
        return new Velocity(
                Money.of(currency, velocity.rollingVolume24h()),
                Money.of(currency, velocity.rollingVolume7d()),
                count(velocity.transactionCount24h()),
                count(velocity.nearThresholdCount7d()),
                Duration.ofSeconds(velocity.dormancySeconds()));
    }

    private static CardSignals cardOf(final CardRow card, final Activity activity) {
        return new CardSignals(
                count(card.declineCount1h()),
                count(card.cardNotPresentDeclineCount1h()),
                count(card.distinctMerchantCount1h()),
                money(card.quasiCashVolume24h(), activity));
    }

    private static PaymentSignals paymentOf(final PaymentRow payment, final Activity activity) {
        return new PaymentSignals(
                countries(payment),
                count(payment.crossBorderCount7d()),
                money(payment.crossBorderVolume7d(), activity));
    }

    private static CryptoSignals cryptoOf(
            final CryptoRow crypto, final Activity activity, final WalletProximity proximity) {
        return new CryptoSignals(
                crypto.destinationIsExchange(),
                sinceInboundFunding(crypto),
                money(crypto.outboundVolume24h(), activity),
                hops(crypto, proximity));
    }

    /**
     * A wallet that was never funded within view has not been holding for nought seconds — it has
     * been holding forever, which is the only reading that keeps a rapid-disposal rule from firing
     * on every transfer whose funding leg happens to be off-chain.
     */
    private static Duration sinceInboundFunding(final CryptoRow crypto) {
        return crypto.sinceInboundFundingSeconds() == null
                ? CryptoSignals.NO_INBOUND_FUNDING
                : Duration.ofSeconds(crypto.sinceInboundFundingSeconds());
    }

    private static int hops(final CryptoRow crypto, final WalletProximity proximity) {
        return crypto.sendingWallet() == null
                ? CryptoSignals.NO_PATH_TO_FLAGGED_WALLET
                : proximity.hopsFrom(crypto.sendingWallet());
    }

    /** {@code CHAR(2)} arrives blank-padded from PostgreSQL; the core insists on exactly two. */
    private static Set<String> countries(final PaymentRow payment) {
        return payment.counterpartyCountries7d().stream().map(String::trim).collect(Collectors.toUnmodifiableSet());
    }

    private static Money money(final BigDecimal amount, final Activity activity) {
        return Money.of(activity.amount().currency(), amount);
    }

    /** A window count is a {@code bigint}; a count that does not fit an {@code int} is a bug upstream. */
    private static int count(final long value) {
        return Math.toIntExact(value);
    }
}
