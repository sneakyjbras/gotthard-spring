package ch.gotthard.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RiskLevelTest {

    @ParameterizedTest
    @CsvSource({
        "0.00, LOW",
        "24.99, LOW",
        "25.00, MEDIUM",
        "49.99, MEDIUM",
        "50.00, HIGH",
        "74.99, HIGH",
        "75.00, CRITICAL",
        "100.00, CRITICAL"
    })
    void given_a_score_when_banded_then_it_lands_in_the_band_that_has_opened(
            final String score, final RiskLevel expected) {
        assertThat(RiskLevel.forScore(new BigDecimal(score))).isEqualTo(expected);
    }

    @Test
    void given_a_score_on_a_band_bound_when_banded_then_the_bound_belongs_to_the_higher_band() {
        assertThat(RiskLevel.forScore(RiskLevel.HIGH.inclusiveLowerBound())).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void given_the_bands_when_read_in_declaration_order_then_their_bounds_ascend() {
        final var bounds = Arrays.stream(RiskLevel.values())
                .map(RiskLevel::inclusiveLowerBound)
                .toList();

        assertThat(bounds).isSorted();
    }

    @Test
    void given_the_lowest_band_when_asked_for_its_bound_then_it_opens_at_nought() {
        assertThat(RiskLevel.LOW.inclusiveLowerBound()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void given_a_negative_score_when_banded_then_it_refuses() {
        assertThatIllegalArgumentException().isThrownBy(() -> RiskLevel.forScore(new BigDecimal("-0.01")));
    }
}
