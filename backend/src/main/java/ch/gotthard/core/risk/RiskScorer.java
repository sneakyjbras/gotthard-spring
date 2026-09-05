package ch.gotthard.core.risk;

import ch.gotthard.core.model.Features;
import ch.gotthard.core.model.Require;
import ch.gotthard.core.model.RiskScore;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Runs the rules over one transaction and adds up what fires.
 *
 * <p>The scorer knows nothing about any particular rule. It selects the rules that speak for the
 * channel, asks each whether it fires, prices the ones that do at the weight the caller supplies,
 * and totals them. A new rule changes none of that, which is the property worth protecting: this
 * class should have no reason to be edited again.
 *
 * <p>Weights arrive per call rather than per instance because they live in a table an operator can
 * update between two evaluations, and a scorer that cached them at construction would go stale.
 */
public final class RiskScorer {

    private final List<Rule> rules;

    public RiskScorer(final List<Rule> rules) {
        this.rules = List.copyOf(Require.present(rules, "rules"));
        rejectDuplicateCodes(this.rules);
    }

    public RiskEvaluation score(final Features features, final RuleWeights weights) {
        Require.present(features, "features");
        Require.present(weights, "weights");
        final List<RuleHit> hits = rules.stream()
                .filter(rule -> rule.appliesTo().contains(features.activityType()))
                .filter(rule -> rule.fires(features))
                .map(rule -> priced(rule, features, weights))
                .flatMap(Optional::stream)
                .toList();
        return new RiskEvaluation(totalOf(hits), hits);
    }

    /** The rules this scorer runs, in the order they were given. */
    public List<Rule> rules() {
        return rules;
    }

    /** A rule with no enabled weight is switched off: it fired, but it does not score. */
    private static Optional<RuleHit> priced(final Rule rule, final Features features, final RuleWeights weights) {
        return weights.weightFor(rule.code())
                .map(weight -> new RuleHit(rule.code(), rule.contribution(features, weight)));
    }

    private static RiskScore totalOf(final List<RuleHit> hits) {
        return RiskScore.of(hits.stream().map(RuleHit::contribution).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** Two rules under one code would mean two {@code risk_rules} rows fighting over one weight. */
    private static void rejectDuplicateCodes(final List<Rule> rules) {
        final List<String> duplicates =
                rules.stream().collect(Collectors.groupingBy(Rule::code, Collectors.counting())).entrySet().stream()
                        .filter(byCode -> byCode.getValue() > 1)
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList();
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("duplicate rule codes: " + duplicates);
        }
    }
}
