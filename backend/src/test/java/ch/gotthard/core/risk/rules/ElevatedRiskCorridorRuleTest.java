package ch.gotthard.core.risk.rules;

import static ch.gotthard.core.Fixtures.chf;
import static ch.gotthard.core.Fixtures.paymentFeatures;
import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.model.PaymentSignals;
import ch.gotthard.core.risk.Rule;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ElevatedRiskCorridorRuleTest {

    private final Rule rule = new ElevatedRiskCorridorRule();

    @Test
    void given_repeated_payments_to_a_listed_jurisdiction_when_evaluated_then_it_fires() {
        assertThat(rule.fires(corridor(Set.of("CH", "IR"), 2, "800.00"))).isTrue();
    }

    @Test
    void given_a_week_of_payments_to_a_listed_jurisdiction_at_size_when_evaluated_then_it_fires() {
        assertThat(rule.fires(corridor(Set.of("SY"), 1, "5000.00"))).isTrue();
    }

    @Test
    void given_one_small_payment_to_a_listed_jurisdiction_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(corridor(Set.of("IR"), 1, "400.00"))).isFalse();
    }

    @Test
    void given_repeated_payments_at_size_to_unlisted_jurisdictions_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(corridor(Set.of("DE", "FR"), 9, "90000.00"))).isFalse();
    }

    @Test
    void given_a_domestic_customer_with_no_corridors_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(paymentFeatures(PaymentSignals.none("CHF")))).isFalse();
    }

    @Test
    void given_the_rule_when_asked_who_it_is_then_it_is_the_corridor_rule_for_payments() {
        assertThat(rule.code()).isEqualTo("R-02");
        assertThat(rule.appliesTo()).containsExactly(ActivityType.PAYMENT);
    }

    private static Features corridor(final Set<String> countries, final int transfers, final String volume) {
        return paymentFeatures(new PaymentSignals(countries, transfers, chf(volume)));
    }
}
