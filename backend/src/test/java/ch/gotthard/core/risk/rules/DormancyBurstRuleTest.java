package ch.gotthard.core.risk.rules;

import static ch.gotthard.core.Fixtures.chf;
import static ch.gotthard.core.Fixtures.featuresWith;
import static ch.gotthard.core.Fixtures.quiet;
import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.model.Velocity;
import ch.gotthard.core.risk.Rule;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DormancyBurstRuleTest {

    private final Rule rule = new DormancyBurstRule();

    @Test
    void given_an_account_silent_for_a_quarter_that_transacts_five_times_in_a_day_when_evaluated_then_it_fires() {
        assertThat(rule.fires(wokeAfter(Duration.ofDays(120), 6))).isTrue();
    }

    @Test
    void given_dormancy_of_exactly_ninety_days_and_a_burst_of_five_when_evaluated_then_it_fires() {
        assertThat(rule.fires(wokeAfter(Duration.ofDays(90), 5))).isTrue();
    }

    @Test
    void given_a_gap_of_a_fortnight_before_the_burst_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(wokeAfter(Duration.ofDays(14), 9))).isFalse();
    }

    @Test
    void given_a_long_dormancy_followed_by_four_transactions_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(wokeAfter(Duration.ofDays(400), 4))).isFalse();
    }

    @Test
    void given_a_customer_first_transaction_when_evaluated_then_it_does_not_read_as_a_return_from_dormancy() {
        assertThat(rule.fires(featuresWith(ActivityType.CARD, quiet()))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ActivityType.class)
    void given_a_burst_on_any_channel_when_evaluated_then_it_fires(final ActivityType type) {
        final Velocity woken = new Velocity(chf("4000.00"), chf("4000.00"), 7, 0, Duration.ofDays(200));

        assertThat(rule.fires(featuresWith(type, woken))).isTrue();
    }

    @Test
    void given_the_rule_when_asked_who_it_is_then_it_is_the_dormancy_rule_for_every_channel() {
        assertThat(rule.code()).isEqualTo("R-06");
        assertThat(rule.appliesTo()).isEqualTo(ActivityType.ALL);
    }

    private static Features wokeAfter(final Duration dormancy, final int burst) {
        final Velocity velocity = new Velocity(chf("4000.00"), chf("4000.00"), burst, 0, dormancy);
        return featuresWith(ActivityType.PAYMENT, velocity);
    }
}
