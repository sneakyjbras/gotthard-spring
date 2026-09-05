package ch.gotthard.core.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleWeightsTest {

    @Test
    void given_a_table_of_weights_when_asked_for_a_known_code_then_it_answers_with_the_weight() {
        final RuleWeights weights = RuleWeights.of(Map.of("R-01", new BigDecimal("25.00")));

        assertThat(weights.weightFor("R-01")).contains(new BigDecimal("25.00"));
    }

    @Test
    void given_a_code_with_no_enabled_row_when_asked_then_it_answers_with_nothing() {
        final RuleWeights weights = RuleWeights.of(Map.of("R-01", new BigDecimal("25.00")));

        assertThat(weights.weightFor("R-02")).isEmpty();
    }

    @Test
    void given_the_weights_when_the_source_map_changes_afterwards_then_the_lookup_is_unaffected() {
        final Map<String, BigDecimal> mutable = new HashMap<>(Map.of("R-01", new BigDecimal("25.00")));
        final RuleWeights weights = RuleWeights.of(mutable);

        mutable.put("R-02", new BigDecimal("40.00"));

        assertThat(weights.weightFor("R-02")).isEmpty();
    }

    @Test
    void given_every_rule_switched_off_when_asked_for_any_code_then_it_answers_with_nothing() {
        assertThat(RuleWeights.none().weightFor("R-01")).isEmpty();
    }
}
