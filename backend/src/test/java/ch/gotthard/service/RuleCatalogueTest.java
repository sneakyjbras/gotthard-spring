package ch.gotthard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.gotthard.domain.model.RiskRule;
import ch.gotthard.domain.model.RuleScope;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Weights are data, and a rule with no data behind it is switched off rather than broken. */
class RuleCatalogueTest {

    @Test
    void given_anEnabledRule_when_weights_then_itsWeightIsTheOneInTheTable() {
        final RuleCatalogue catalogue = RuleCatalogue.of(List.of(rule("R-01", "12.50", true)));

        assertThat(catalogue.weights().weightFor("R-01")).contains(new BigDecimal("12.50"));
    }

    /** Switching a rule off is an {@code UPDATE}: it stops scoring without anything being deployed. */
    @Test
    void given_aDisabledRule_when_weights_then_itHasNoWeightAndDoesNotScore() {
        final RuleCatalogue catalogue = RuleCatalogue.of(List.of(rule("R-01", "12.50", false)));

        assertThat(catalogue.weights().weightFor("R-01")).isEmpty();
        assertThat(catalogue.rule("R-01")).isEmpty();
    }

    @Test
    void given_anEmptyTable_when_weights_then_everyRuleIsInert() {
        assertThat(RuleCatalogue.of(List.of()).weights().weightFor("R-01")).isEmpty();
    }

    /** A code that scored must have had a row; not finding one means the table moved mid-evaluation. */
    @Test
    void given_aCodeWithNoEnabledRow_when_require_then_itFailsRatherThanGuessing() {
        assertThatThrownBy(() -> RuleCatalogue.of(List.of()).require("R-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("R-01");
    }

    private static RiskRule rule(final String code, final String weight, final boolean enabled) {
        return new RiskRule(
                UUID.randomUUID(), code, "Test rule", RuleScope.ALL, "amount > 10000", new BigDecimal(weight), enabled);
    }
}
