package ch.gotthard.core.risk.rules;

import static ch.gotthard.core.Fixtures.cardFeatures;
import static ch.gotthard.core.Fixtures.chf;
import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.CardSignals;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.risk.Rule;
import org.junit.jupiter.api.Test;

class CardNotPresentDeclineClusterRuleTest {

    private final Rule rule = new CardNotPresentDeclineClusterRule();

    @Test
    void given_three_card_not_present_declines_across_two_merchants_when_evaluated_then_it_fires() {
        assertThat(rule.fires(hourOf(3, 3, 2))).isTrue();
    }

    @Test
    void given_a_long_run_of_declines_across_many_merchants_when_evaluated_then_it_fires() {
        assertThat(rule.fires(hourOf(11, 9, 7))).isTrue();
    }

    @Test
    void given_two_card_not_present_declines_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(hourOf(2, 2, 2))).isFalse();
    }

    @Test
    void given_declines_at_a_single_merchant_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(hourOf(5, 5, 1))).isFalse();
    }

    @Test
    void given_declines_where_the_card_was_present_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(hourOf(5, 0, 3))).isFalse();
    }

    @Test
    void given_a_customer_with_no_declines_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(cardFeatures(CardSignals.none("CHF")))).isFalse();
    }

    @Test
    void given_the_rule_when_asked_who_it_is_then_it_is_the_card_testing_rule_for_cards() {
        assertThat(rule.code()).isEqualTo("R-03");
        assertThat(rule.appliesTo()).containsExactly(ActivityType.CARD);
    }

    private static Features hourOf(final int declines, final int cardNotPresent, final int merchants) {
        return cardFeatures(new CardSignals(declines, cardNotPresent, merchants, chf("0.00")));
    }
}
