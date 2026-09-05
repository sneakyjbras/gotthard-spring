package ch.gotthard.core.risk.rules;

import static ch.gotthard.core.Fixtures.chf;
import static ch.gotthard.core.Fixtures.cryptoFeatures;
import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.CryptoSignals;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.risk.Rule;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FlaggedWalletProximityRuleTest {

    private static final BigDecimal WEIGHT = new BigDecimal("30.00");

    private final Rule rule = new FlaggedWalletProximityRule();

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void given_a_wallet_within_two_hops_of_a_flagged_address_when_evaluated_then_it_fires(final int hops) {
        assertThat(rule.fires(hopsAway(hops))).isTrue();
    }

    @Test
    void given_a_wallet_three_hops_from_a_flagged_address_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(hopsAway(3))).isFalse();
    }

    @Test
    void given_no_flagged_address_within_the_search_depth_when_evaluated_then_it_does_not_fire() {
        assertThat(rule.fires(hopsAway(CryptoSignals.NO_PATH_TO_FLAGGED_WALLET)))
                .isFalse();
    }

    @Test
    void given_a_wallet_dealing_directly_with_a_flagged_address_when_priced_then_it_is_worth_the_full_weight() {
        assertThat(rule.contribution(hopsAway(1), WEIGHT)).isEqualByComparingTo(WEIGHT);
    }

    @Test
    void given_one_wallet_between_it_and_a_flagged_address_when_priced_then_it_is_worth_half_the_weight() {
        assertThat(rule.contribution(hopsAway(2), WEIGHT)).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    void given_the_flagged_address_itself_when_priced_then_it_is_worth_the_full_weight() {
        assertThat(rule.contribution(hopsAway(0), WEIGHT)).isEqualByComparingTo(WEIGHT);
    }

    @Test
    void given_the_rule_when_asked_who_it_is_then_it_is_the_proximity_rule_for_crypto() {
        assertThat(rule.code()).isEqualTo("R-05");
        assertThat(rule.appliesTo()).containsExactly(ActivityType.CRYPTO);
    }

    private static Features hopsAway(final int hops) {
        return cryptoFeatures(new CryptoSignals(false, Duration.ofDays(2), chf("500.00"), hops));
    }
}
