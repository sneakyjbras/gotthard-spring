package ch.gotthard.core.risk.rules;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.model.Velocity;
import ch.gotthard.core.risk.Rule;
import java.math.BigDecimal;
import java.util.Set;

/**
 * R-01 — structuring. Three or more amounts placed just below the reporting threshold within seven
 * days, coming to the threshold or more in total.
 *
 * <p>One payment of 9 500 is a payment. Four of them in a week, adding to 38 000, is someone who
 * knows where the reporting line sits. The channel does not matter — the same behaviour shows up in
 * cards, transfers and crypto — so this rule speaks for all three.
 */
public final class NearThresholdStructuringRule implements Rule {

    public static final String CODE = "R-01";

    /** Two near-threshold amounts is a coincidence; three is a pattern. */
    public static final int MIN_NEAR_THRESHOLD_ACTIVITIES = 3;

    /** The reporting threshold itself, in the customer's reporting currency. */
    public static final BigDecimal MIN_AGGREGATE_VOLUME = new BigDecimal("10000.00");

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
        return velocity.nearThresholdCount7d() >= MIN_NEAR_THRESHOLD_ACTIVITIES
                && velocity.rollingVolume7d().hasAmountAtLeast(MIN_AGGREGATE_VOLUME);
    }
}
