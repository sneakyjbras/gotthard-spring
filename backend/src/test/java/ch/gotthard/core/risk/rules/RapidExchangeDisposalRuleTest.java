package ch.gotthard.core.risk.rules;

import static ch.gotthard.core.Fixtures.chf;
import static ch.gotthard.core.Fixtures.cryptoFeatures;
import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.CryptoSignals;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.risk.Rule;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RapidExchangeDisposalRuleTest {

    private final Rule rule = new RapidExchangeDisposalRule();

    @Test
    void given_funds_sent_to_an_exchange_within_the_hour_at_size_when_evaluated_then_it_fires() {
        assertThat(rule.fires(disposal(true, Duration.ofMinutes(12), "24000.00")))
                .isTrue();
    }

    @Test
    void given_a_transfer_exactly_on_the_hour_and_the_volume_bound_when_evaluated_then_it_fires() {
        assertThat(rule.fires(disposal(true, Duration.ofHours(1), "10000.00"))).isTrue();
    }

    @Test
    void given_funds_sent_on_to_a_private_wallet_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(disposal(false, Duration.ofMinutes(12), "24000.00")))
                .isFalse();
    }

    @Test
    void given_funds_held_for_a_day_before_the_exchange_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(disposal(true, Duration.ofHours(26), "24000.00"))).isFalse();
    }

    @Test
    void given_a_small_transfer_to_an_exchange_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(disposal(true, Duration.ofMinutes(12), "900.00"))).isFalse();
    }

    @Test
    void given_no_inbound_funding_observed_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(disposal(true, CryptoSignals.NO_INBOUND_FUNDING, "24000.00")))
                .isFalse();
    }

    @Test
    void given_the_rule_when_asked_who_it_is_then_it_is_the_rapid_disposal_rule_for_crypto() {
        assertThat(rule.code()).isEqualTo("R-04");
        assertThat(rule.appliesTo()).containsExactly(ActivityType.CRYPTO);
    }

    private static Features disposal(final boolean toExchange, final Duration held, final String dayVolume) {
        return cryptoFeatures(
                new CryptoSignals(toExchange, held, chf(dayVolume), CryptoSignals.NO_PATH_TO_FLAGGED_WALLET));
    }
}
