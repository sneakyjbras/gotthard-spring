package ch.gotthard.core.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.gotthard.core.model.RiskLevel;
import ch.gotthard.core.model.RiskScore;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskEvaluationTest {

    @Test
    void given_nothing_fired_when_evaluated_then_the_transaction_is_clean_and_low() {
        final RiskEvaluation clean = RiskEvaluation.clean();

        assertThat(clean.hits()).isEmpty();
        assertThat(clean.score().level()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void given_rules_that_fired_when_asked_for_the_signals_then_it_lists_their_codes_in_order() {
        final RiskEvaluation evaluation = new RiskEvaluation(
                RiskScore.of(new BigDecimal("55.00")),
                List.of(new RuleHit("R-02", new BigDecimal("30.00")), new RuleHit("R-06", new BigDecimal("25.00"))));

        assertThat(evaluation.firedRuleCodes()).containsExactly("R-02", "R-06");
    }

    @Test
    void given_the_hits_when_the_source_list_changes_afterwards_then_the_evaluation_is_unaffected() {
        final List<RuleHit> mutable = new ArrayList<>(List.of(new RuleHit("R-02", new BigDecimal("30.00"))));
        final RiskEvaluation evaluation = new RiskEvaluation(RiskScore.of(new BigDecimal("30.00")), mutable);

        mutable.add(new RuleHit("R-06", new BigDecimal("25.00")));

        assertThat(evaluation.hits()).hasSize(1);
    }

    @Test
    void given_the_hits_when_a_caller_tries_to_add_one_then_the_list_refuses() {
        final RiskEvaluation evaluation = RiskEvaluation.clean();

        assertThatThrownBy(() -> evaluation.hits().add(new RuleHit("R-01", BigDecimal.ONE)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void given_a_negative_contribution_when_a_hit_is_recorded_then_it_refuses() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RuleHit("R-01", new BigDecimal("-1.00")));
    }

    @Test
    void given_a_contribution_with_a_finer_scale_when_recorded_then_it_is_stored_to_two_places() {
        assertThat(new RuleHit("R-05", new BigDecimal("12.5")).contribution()).isEqualTo(new BigDecimal("12.50"));
    }
}
