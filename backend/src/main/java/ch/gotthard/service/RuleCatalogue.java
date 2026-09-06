package ch.gotthard.service;

import ch.gotthard.core.risk.RuleWeights;
import ch.gotthard.domain.model.RiskRule;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The {@code risk_rules} table, loaded and indexed by rule code.
 *
 * <p>Weights are data. They live in a column an operator can {@code UPDATE} between two evaluations,
 * which is why the scorer takes them per call and why this snapshot is taken per call too — a
 * catalogue cached at startup would keep scoring against last week's tuning.
 *
 * <p>A rule with no row, or with {@code enabled = false}, is simply absent from the map. The core
 * already treats an absent weight as "this rule does not score", so switching a rule off is an
 * {@code UPDATE} and an empty table is a system that computes nought rather than one that breaks.
 */
public record RuleCatalogue(Map<String, RiskRule> enabledByCode) {

    public RuleCatalogue {
        enabledByCode = Map.copyOf(enabledByCode);
    }

    /** Indexes the enabled rows; disabled ones are dropped here rather than checked later. */
    public static RuleCatalogue of(final Collection<RiskRule> rules) {
        return new RuleCatalogue(rules.stream()
                .filter(RiskRule::isEnabled)
                .collect(Collectors.toUnmodifiableMap(RiskRule::getRuleCode, Function.identity())));
    }

    /** What the scorer needs: a weight per code, empty for anything not enabled. */
    public RuleWeights weights() {
        return ruleCode -> rule(ruleCode).map(RiskRule::getWeight);
    }

    /** The row behind a code, for writing the assessment that names it. */
    public Optional<RiskRule> rule(final String ruleCode) {
        return Optional.ofNullable(enabledByCode.get(ruleCode));
    }

    /**
     * The row behind a code that has already scored. A rule only scores when this catalogue supplied
     * its weight, so an absent row here means the catalogue changed underneath a running evaluation.
     */
    public RiskRule require(final String ruleCode) {
        return rule(ruleCode)
                .orElseThrow(() -> new IllegalStateException(ruleCode + " scored but has no enabled risk_rules row"));
    }
}
