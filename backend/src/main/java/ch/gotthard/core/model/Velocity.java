package ch.gotthard.core.model;

import java.time.Duration;

/**
 * How fast money has been moving, whatever channel it moved through.
 *
 * <p>Every component is a window aggregate the feature query computes — rolling sums over a
 * preceding interval, a count of amounts sitting just under the reporting threshold, and the gap to
 * the customer's previous activity. The core consumes them; it never derives them.
 *
 * @param rollingVolume24h summed amount over the twenty-four hours ending at this activity
 * @param rollingVolume7d summed amount over the seven days ending at this activity
 * @param transactionCount24h activities in the same twenty-four hours, this one included
 * @param nearThresholdCount7d activities in the last seven days whose amount fell just below the
 *     reporting threshold — the shape structuring leaves behind
 * @param dormancyBeforeActivity gap since the previous activity, {@link Duration#ZERO} when there
 *     was none, so a customer's first transaction never reads as a return from dormancy
 */
public record Velocity(
        Money rollingVolume24h,
        Money rollingVolume7d,
        int transactionCount24h,
        int nearThresholdCount7d,
        Duration dormancyBeforeActivity) {

    public Velocity {
        Require.present(rollingVolume24h, "rollingVolume24h");
        Require.present(rollingVolume7d, "rollingVolume7d");
        Require.notNegative(transactionCount24h, "transactionCount24h");
        Require.notNegative(nearThresholdCount7d, "nearThresholdCount7d");
        Require.notNegative(dormancyBeforeActivity, "dormancyBeforeActivity");
    }

    /** A customer with no history behind this activity. */
    public static Velocity none(final String currency) {
        return new Velocity(Money.zero(currency), Money.zero(currency), 0, 0, Duration.ZERO);
    }
}
