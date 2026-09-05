package ch.gotthard.core.risk;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Where a rule's weight comes from: {@code risk_rules}, by rule code.
 *
 * <p>The core states the need and someone above it satisfies it — the whole point of keeping weights
 * out of the code that uses them. An absent weight is not an error: a rule whose row is missing or
 * disabled simply does not score, so switching a rule off is an {@code UPDATE} rather than a deploy.
 */
@FunctionalInterface
public interface RuleWeights {

    /** The weight for a rule code, or empty when no enabled rule row carries that code. */
    Optional<BigDecimal> weightFor(String ruleCode);

    /** A fixed table of weights, for tests and for callers that loaded the rows themselves. */
    static RuleWeights of(final Map<String, BigDecimal> weights) {
        final Map<String, BigDecimal> snapshot = Map.copyOf(weights);
        return ruleCode -> Optional.ofNullable(snapshot.get(ruleCode));
    }

    /** Every rule switched off. Scoring against it yields nought and no assessments. */
    static RuleWeights none() {
        return ruleCode -> Optional.empty();
    }
}
