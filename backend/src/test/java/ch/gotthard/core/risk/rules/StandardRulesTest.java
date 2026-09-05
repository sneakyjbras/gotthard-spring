package ch.gotthard.core.risk.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.risk.RiskScorer;
import ch.gotthard.core.risk.Rule;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StandardRulesTest {

    private final List<Rule> rules = StandardRules.all();

    @Test
    void given_the_shipped_rules_when_listed_then_every_code_is_distinct() {
        assertThat(rules).extracting(Rule::code).doesNotHaveDuplicates();
    }

    @Test
    void given_the_shipped_rules_when_listed_then_each_code_reads_as_a_rule_code() {
        assertThat(rules).extracting(Rule::code).allMatch(code -> code.matches("R-\\d{2}"));
    }

    @ParameterizedTest
    @EnumSource(ActivityType.class)
    void given_a_channel_when_the_shipped_rules_are_asked_then_at_least_one_speaks_for_it(final ActivityType type) {
        assertThat(rules).anyMatch(rule -> rule.appliesTo().contains(type));
    }

    @Test
    void given_the_shipped_rules_when_handed_to_the_scorer_then_it_accepts_them() {
        assertThatNoException().isThrownBy(() -> new RiskScorer(rules));
    }

    @Test
    void given_the_shipped_rules_when_counted_then_the_catalogue_covers_the_published_model() {
        assertThat(rules).hasSize(7);
    }
}
