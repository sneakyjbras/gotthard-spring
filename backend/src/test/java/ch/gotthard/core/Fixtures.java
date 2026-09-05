package ch.gotthard.core;

import ch.gotthard.core.model.Activity;
import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.CardSignals;
import ch.gotthard.core.model.CryptoSignals;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.model.Money;
import ch.gotthard.core.model.PaymentSignals;
import ch.gotthard.core.model.Velocity;
import java.time.Instant;
import java.util.UUID;

/**
 * The uninteresting half of a test's setup.
 *
 * <p>A rule test is about one signal. Everything else — who the customer is, when it happened, the
 * two channels the transaction is not — is noise, and lives here so the test reads as the condition
 * it is checking.
 */
public final class Fixtures {

    public static final String CURRENCY = "CHF";

    private static final Instant WHEN = Instant.parse("2026-03-01T10:15:30Z");

    private Fixtures() {}

    public static Money chf(final String amount) {
        return Money.of(CURRENCY, amount);
    }

    public static Activity activity(final ActivityType type) {
        return new Activity(UUID.randomUUID(), UUID.randomUUID(), type, chf("1000.00"), WHEN);
    }

    /** A customer with no history: every rolling signal at nought. */
    public static Velocity quiet() {
        return Velocity.none(CURRENCY);
    }

    public static Features cardFeatures(final CardSignals card) {
        return Features.card(activity(ActivityType.CARD), quiet(), card);
    }

    public static Features paymentFeatures(final PaymentSignals payment) {
        return Features.payment(activity(ActivityType.PAYMENT), quiet(), payment);
    }

    public static Features cryptoFeatures(final CryptoSignals crypto) {
        return Features.crypto(activity(ActivityType.CRYPTO), quiet(), crypto);
    }

    /** Features carrying velocity alone, for the rules that read nothing channel-specific. */
    public static Features featuresWith(final ActivityType type, final Velocity velocity) {
        return switch (type) {
            case CARD -> Features.card(activity(type), velocity, CardSignals.none(CURRENCY));
            case PAYMENT -> Features.payment(activity(type), velocity, PaymentSignals.none(CURRENCY));
            case CRYPTO -> Features.crypto(activity(type), velocity, CryptoSignals.none(CURRENCY));
        };
    }
}
