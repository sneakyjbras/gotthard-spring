package ch.gotthard.core.risk;

import static ch.gotthard.core.Fixtures.cardFeatures;
import static ch.gotthard.core.Fixtures.featuresWith;
import static ch.gotthard.core.Fixtures.quiet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.CardSignals;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.model.RiskLevel;
import ch.gotthard.core.model.RiskScore;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RiskScorerTest {

    private static final Features CARD = cardFeatures(CardSignals.none("CHF"));

    /** Three weights that add to more than the scale allows, so clamping can be tested. */
    private static final RuleWeights WEIGHTS = RuleWeights.of(
            Map.of("R-A", new BigDecimal("20.00"), "R-B", new BigDecimal("15.00"), "R-C", new BigDecimal("80.00")));

    @Test
    void given_no_rule_fires_when_scored_then_the_score_is_nought_and_the_level_is_low() {
        final RiskScorer scorer = new RiskScorer(List.of(silent("R-A")));

        final RiskEvaluation evaluation = scorer.score(CARD, WEIGHTS);

        assertThat(evaluation.hits()).isEmpty();
        assertThat(evaluation.score()).isEqualTo(RiskScore.zero());
        assertThat(evaluation.score().level()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void given_two_rules_fire_when_scored_then_their_weights_are_summed_and_banded() {
        final RiskScorer scorer = new RiskScorer(List.of(firing("R-A"), firing("R-B")));

        final RiskEvaluation evaluation = scorer.score(CARD, WEIGHTS);

        assertThat(evaluation.score().value()).isEqualTo(new BigDecimal("35.00"));
        assertThat(evaluation.score().level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(evaluation.firedRuleCodes()).containsExactly("R-A", "R-B");
    }

    @Test
    void given_a_rule_for_another_channel_when_a_card_is_scored_then_it_is_never_consulted() {
        final RiskScorer scorer = new RiskScorer(List.of(new ExplodingRule("R-A", Set.of(ActivityType.CRYPTO))));

        assertThat(scorer.score(CARD, WEIGHTS).hits()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ActivityType.class)
    void given_a_channel_agnostic_rule_when_any_channel_is_scored_then_it_is_consulted(final ActivityType type) {
        final RiskScorer scorer = new RiskScorer(List.of(new FixedRule("R-A", ActivityType.ALL, true)));

        final RiskEvaluation evaluation = scorer.score(featuresWith(type, quiet()), WEIGHTS);

        assertThat(evaluation.firedRuleCodes()).containsExactly("R-A");
    }

    @Test
    void given_a_firing_rule_with_no_weight_row_when_scored_then_it_records_nothing() {
        final RiskScorer scorer = new RiskScorer(List.of(firing("R-UNKNOWN")));

        final RiskEvaluation evaluation = scorer.score(CARD, WEIGHTS);

        assertThat(evaluation.hits()).isEmpty();
        assertThat(evaluation.score()).isEqualTo(RiskScore.zero());
    }

    @Test
    void given_contributions_beyond_the_scale_when_scored_then_the_total_is_clamped_to_a_hundred() {
        final RiskScorer scorer = new RiskScorer(List.of(firing("R-A"), firing("R-B"), firing("R-C")));

        final RiskEvaluation evaluation = scorer.score(CARD, WEIGHTS);

        assertThat(evaluation.score().value()).isEqualTo(RiskScore.MAXIMUM);
        assertThat(evaluation.hits()).hasSize(3);
    }

    @Test
    void given_a_rule_that_grades_its_own_severity_when_scored_then_its_contribution_is_used() {
        final RiskScorer scorer = new RiskScorer(List.of(new GradedRule("R-A", new BigDecimal("0.25"))));

        final RiskEvaluation evaluation = scorer.score(CARD, WEIGHTS);

        assertThat(evaluation.hits()).containsExactly(new RuleHit("R-A", new BigDecimal("5.00")));
    }

    @Test
    void given_two_rules_sharing_a_code_when_the_scorer_is_built_then_it_refuses() {
        final List<Rule> clashing = List.of(firing("R-A"), silent("R-A"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RiskScorer(clashing))
                .withMessageContaining("duplicate rule codes: [R-A]");
    }

    @Test
    void given_every_rule_switched_off_when_scored_then_nothing_is_recorded() {
        final RiskScorer scorer = new RiskScorer(List.of(firing("R-A"), firing("R-B")));

        assertThat(scorer.score(CARD, RuleWeights.none())).isEqualTo(RiskEvaluation.clean());
    }

    @Test
    void given_the_rules_when_the_source_list_changes_afterwards_then_the_scorer_is_unaffected() {
        final List<Rule> mutable = new ArrayList<>(List.of(firing("R-A")));
        final RiskScorer scorer = new RiskScorer(mutable);

        mutable.add(firing("R-B"));

        assertThat(scorer.rules()).hasSize(1);
        assertThat(scorer.score(CARD, WEIGHTS).firedRuleCodes()).containsExactly("R-A");
    }

    private static Rule firing(final String code) {
        return new FixedRule(code, ActivityType.ALL, true);
    }

    private static Rule silent(final String code) {
        return new FixedRule(code, ActivityType.ALL, false);
    }

    /** A rule whose firing is decided by the test rather than by any signal. */
    private record FixedRule(String code, Set<ActivityType> appliesTo, boolean firing) implements Rule {
        @Override
        public boolean fires(final Features features) {
            return firing;
        }
    }

    /** A rule that prices itself at a fraction of its weight, as a proximity rule does. */
    private record GradedRule(String code, BigDecimal factor) implements Rule {
        @Override
        public Set<ActivityType> appliesTo() {
            return ActivityType.ALL;
        }

        @Override
        public boolean fires(final Features features) {
            return true;
        }

        @Override
        public BigDecimal contribution(final Features features, final BigDecimal weight) {
            return weight.multiply(factor);
        }
    }

    /** A rule that fails the test if the scorer ever asks it about the wrong channel. */
    private record ExplodingRule(String code, Set<ActivityType> appliesTo) implements Rule {
        @Override
        public boolean fires(final Features features) {
            throw new AssertionError("rule " + code + " was consulted for " + features.activityType());
        }
    }
}
