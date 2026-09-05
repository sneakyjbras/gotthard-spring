package ch.gotthard.core.model;

/**
 * Everything a rule is allowed to see about one transaction.
 *
 * <p>The signals are precomputed — window functions over the customer's timeline for the velocity
 * and channel aggregates, a depth-limited walk of the wallet graph for the crypto distances. By the
 * time a {@code Features} exists the arithmetic is done, which is what lets a rule be three lines
 * and lets every rule test run without a database.
 *
 * <p>A transaction belongs to one channel, so the other two channels' signals are present and empty
 * rather than absent. Use the channel factories and they are filled in for you.
 */
public record Features(
        Activity activity, Velocity velocity, CardSignals card, PaymentSignals payment, CryptoSignals crypto) {

    public Features {
        Require.present(activity, "activity");
        Require.present(velocity, "velocity");
        Require.present(card, "card");
        Require.present(payment, "payment");
        Require.present(crypto, "crypto");
    }

    public static Features card(final Activity activity, final Velocity velocity, final CardSignals card) {
        final String currency = reportingCurrencyOf(activity, ActivityType.CARD);
        return new Features(activity, velocity, card, PaymentSignals.none(currency), CryptoSignals.none(currency));
    }

    public static Features payment(final Activity activity, final Velocity velocity, final PaymentSignals payment) {
        final String currency = reportingCurrencyOf(activity, ActivityType.PAYMENT);
        return new Features(activity, velocity, CardSignals.none(currency), payment, CryptoSignals.none(currency));
    }

    public static Features crypto(final Activity activity, final Velocity velocity, final CryptoSignals crypto) {
        final String currency = reportingCurrencyOf(activity, ActivityType.CRYPTO);
        return new Features(activity, velocity, CardSignals.none(currency), PaymentSignals.none(currency), crypto);
    }

    public ActivityType activityType() {
        return activity.type();
    }

    private static String reportingCurrencyOf(final Activity activity, final ActivityType expected) {
        if (Require.present(activity, "activity").type() != expected) {
            throw new IllegalArgumentException("expected a " + expected + " activity, was " + activity.type());
        }
        return activity.amount().currency();
    }
}
