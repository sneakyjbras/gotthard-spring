package ch.gotthard.core.risk.rules;

import static ch.gotthard.core.Fixtures.chf;
import static ch.gotthard.core.Fixtures.featuresWith;
import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.model.Velocity;
import ch.gotthard.core.risk.Rule;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NearThresholdStructuringRuleTest {

    private final Rule rule = new NearThresholdStructuringRule();

    @Test
    void given_four_near_threshold_amounts_totalling_more_than_the_threshold_when_evaluated_then_it_fires() {
        assertThat(rule.fires(weekOf(4, "38000.00"))).isTrue();
    }

    @Test
    void given_exactly_three_near_threshold_amounts_at_the_threshold_when_evaluated_then_it_fires() {
        assertThat(rule.fires(weekOf(3, "10000.00"))).isTrue();
    }

    @Test
    void given_only_two_near_threshold_amounts_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(weekOf(2, "19000.00"))).isFalse();
    }

    @Test
    void given_near_threshold_amounts_that_stay_below_the_threshold_in_total_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(weekOf(3, "9999.99"))).isFalse();
    }

    @Test
    void given_a_customer_with_no_history_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(weekOf(0, "0.00"))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ActivityType.class)
    void given_structuring_on_any_channel_when_evaluated_then_it_fires(final ActivityType type) {
        final Velocity structuring = new Velocity(chf("19000.00"), chf("38000.00"), 2, 4, Duration.ofHours(2));

        assertThat(rule.fires(featuresWith(type, structuring))).isTrue();
    }

    @Test
    void given_the_rule_when_asked_who_it_is_then_it_is_the_structuring_rule_for_every_channel() {
        assertThat(rule.code()).isEqualTo("R-01");
        assertThat(rule.appliesTo()).isEqualTo(ActivityType.ALL);
    }

    private static Features weekOf(final int nearThresholdCount, final String weekVolume) {
        final Velocity velocity =
                new Velocity(chf("0.00"), chf(weekVolume), 1, nearThresholdCount, Duration.ofHours(2));
        return featuresWith(ActivityType.PAYMENT, velocity);
    }
}
