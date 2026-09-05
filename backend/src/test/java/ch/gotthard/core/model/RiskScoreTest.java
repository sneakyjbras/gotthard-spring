package ch.gotthard.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RiskScoreTest {

    @Test
    void given_a_total_beyond_the_scale_when_made_with_of_then_it_is_clamped_to_a_hundred() {
        assertThat(RiskScore.of(new BigDecimal("143.75")).value()).isEqualTo(RiskScore.MAXIMUM);
    }

    @Test
    void given_a_negative_total_when_made_with_of_then_it_is_clamped_to_nought() {
        assertThat(RiskScore.of(new BigDecimal("-5.00")).value()).isEqualTo(RiskScore.MINIMUM);
    }

    @Test
    void given_a_score_beyond_the_scale_when_constructed_directly_then_it_refuses() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RiskScore(new BigDecimal("100.01")))
                .withMessageContaining("outside 0..100");
    }

    @Test
    void given_a_value_with_a_finer_scale_when_constructed_then_it_is_stored_to_two_places() {
        assertThat(new RiskScore(new BigDecimal("42.125")).value()).isEqualTo(new BigDecimal("42.13"));
    }

    @Test
    void given_a_score_in_the_upper_band_when_asked_for_its_level_then_it_reports_critical() {
        assertThat(RiskScore.of(new BigDecimal("81.00")).level()).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void given_a_clean_transaction_when_scored_at_nought_then_it_is_low() {
        assertThat(RiskScore.zero().level()).isEqualTo(RiskLevel.LOW);
    }
}
