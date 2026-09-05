package ch.gotthard.core.risk.rules;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.model.Velocity;
import ch.gotthard.core.risk.Rule;
import java.time.Duration;
import java.util.Set;

/**
 * R-06 — dormancy then burst. An account silent for ninety days wakes up and transacts five or more
 * times inside a day.
 *
 * <p>The pattern a taken-over or rented account leaves: nothing for a quarter, then a day's work.
 * The dormancy on its own is a customer on sabbatical, and the burst on its own is a busy week; it
 * is the two together, in that order, that is worth an operator's attention. The channel is
 * immaterial, so this rule speaks for all three.
 */
public final class DormancyBurstRule implements Rule {

    public static final String CODE = "R-06";

    /** A quarter of silence. Shorter gaps are holidays. */
    public static final Duration MIN_DORMANCY = Duration.ofDays(90);

    /** Waking up to transact once is waking up. Five times in a day is a burst. */
    public static final int MIN_BURST_ACTIVITIES = 5;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<ActivityType> appliesTo() {
        return ActivityType.ALL;
    }

    @Override
    public boolean fires(final Features features) {
        final Velocity velocity = features.velocity();
        return velocity.dormancyBeforeActivity().compareTo(MIN_DORMANCY) >= 0
                && velocity.transactionCount24h() >= MIN_BURST_ACTIVITIES;
    }
}
