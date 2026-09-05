package ch.gotthard.core.risk.rules;

import static ch.gotthard.core.Fixtures.cardFeatures;
import static ch.gotthard.core.Fixtures.chf;
import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.CardSignals;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.risk.Rule;
import org.junit.jupiter.api.Test;

class QuasiCashConcentrationRuleTest {

    private final Rule rule = new QuasiCashConcentrationRule();

    @Test
    void given_a_day_of_quasi_cash_spend_beyond_the_limit_when_evaluated_then_it_fires() {
        assertThat(rule.fires(quasiCashOf("7400.00"))).isTrue();
    }

    @Test
    void given_quasi_cash_spend_exactly_at_the_limit_when_evaluated_then_it_fires() {
        assertThat(rule.fires(quasiCashOf("2500.00"))).isTrue();
    }

    @Test
    void given_quasi_cash_spend_a_penny_below_the_limit_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(quasiCashOf("2499.99"))).isFalse();
    }

    @Test
    void given_a_customer_who_spends_nothing_in_those_categories_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(cardFeatures(CardSignals.none("CHF")))).isFalse();
    }

    @Test
    void given_the_rule_when_asked_who_it_is_then_it_is_the_quasi_cash_rule_for_cards() {
        assertThat(rule.code()).isEqualTo("R-07");
        assertThat(rule.appliesTo()).containsExactly(ActivityType.CARD);
    }

    private static Features quasiCashOf(final String dayVolume) {
        return cardFeatures(new CardSignals(0, 0, 3, chf(dayVolume)));
    }
}
