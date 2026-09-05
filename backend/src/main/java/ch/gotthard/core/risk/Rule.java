package ch.gotthard.core.risk;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.Features;
import java.math.BigDecimal;
import java.util.Set;

/**
 * One reason a transaction might be risky.
 *
 * <p>A rule knows four things and no more: what it is called, which channels it speaks for, whether
 * it fires on a given set of features, and what firing is worth. It does not know about the other
 * rules, the running total, or the band the total lands in — which is why a new rule is a new class
 * and a new {@code risk_rules} row, and never an edit to {@link RiskScorer}.
 *
 * <p>What firing is worth is not the rule's to decide either. The weight is data, held in {@code
 * risk_rules.weight} and passed in; tuning the model is an {@code UPDATE}, not a release.
 */
public interface Rule {

    /** Binds this class to its {@code risk_rules} row. Stable for the life of the rule, e.g. R-04. */
    String code();

    /**
     * The channels this rule speaks for — one {@link ActivityType}, or {@link ActivityType#ALL} for
     * the channel-agnostic ones. Mirrors {@code risk_rules.applies_to}.
     */
    Set<ActivityType> appliesTo();

    /** Whether the condition holds. The human-readable form of it is this class's javadoc. */
    boolean fires(Features features);

    /**
     * What firing is worth, asked only after {@link #fires(Features)} has said yes.
     *
     * <p>The weight as given, unless a rule has a reason to grade its own severity — a wallet one
     * hop from a flagged address is worse than one three hops away, and the rule that measures the
     * distance is the only thing that knows it.
     */
    default BigDecimal contribution(final Features features, final BigDecimal weight) {
        return weight;
    }
}
