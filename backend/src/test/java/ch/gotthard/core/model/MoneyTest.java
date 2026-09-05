package ch.gotthard.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void given_an_amount_with_a_finer_scale_when_constructed_then_it_is_normalised_to_two_places() {
        final Money money = Money.of("CHF", new BigDecimal("9500.005"));

        assertThat(money.amount()).isEqualTo(new BigDecimal("9500.01"));
    }

    @Test
    void given_two_amounts_written_differently_when_compared_as_records_then_they_are_equal() {
        assertThat(Money.of("CHF", "10")).isEqualTo(Money.of("CHF", "10.00"));
    }

    @Test
    void given_a_lower_case_currency_when_constructed_then_it_is_normalised_to_upper_case() {
        assertThat(Money.of("chf", "1.00").currency()).isEqualTo("CHF");
    }

    @Test
    void given_amounts_in_one_currency_when_added_then_the_sum_keeps_that_currency() {
        final Money sum = Money.of("CHF", "1200.50").plus(Money.of("CHF", "800.50"));

        assertThat(sum).isEqualTo(Money.of("CHF", "2001.00"));
    }

    @Test
    void given_amounts_in_different_currencies_when_added_then_it_refuses() {
        final Money francs = Money.of("CHF", "100.00");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> francs.plus(Money.of("EUR", "100.00")))
                .withMessageContaining("currency mismatch");
    }

    @Test
    void given_amounts_in_different_currencies_when_compared_then_it_refuses() {
        final Money francs = Money.of("CHF", "100.00");

        assertThatIllegalArgumentException().isThrownBy(() -> francs.isAtLeast(Money.of("EUR", "1.00")));
    }

    @Test
    void given_an_equal_amount_in_the_same_currency_when_compared_then_it_is_at_least_that_much() {
        assertThat(Money.of("CHF", "100.00").isAtLeast(Money.of("CHF", "100.00")))
                .isTrue();
    }

    @Test
    void given_a_smaller_amount_when_compared_then_it_is_not_at_least_the_larger_one() {
        assertThat(Money.of("CHF", "99.99").isAtLeast(Money.of("CHF", "100.00")))
                .isFalse();
    }

    @Test
    void given_a_policy_figure_when_compared_by_amount_then_the_currency_is_ignored() {
        final Money crypto = Money.of("USDT", "12000.00");

        assertThat(crypto.hasAmountAtLeast(new BigDecimal("10000.00"))).isTrue();
    }

    @Test
    void given_an_amount_below_a_policy_figure_when_compared_by_amount_then_it_falls_short() {
        assertThat(Money.of("CHF", "9999.99").hasAmountAtLeast(new BigDecimal("10000.00")))
                .isFalse();
    }

    @Test
    void given_zero_for_a_currency_when_asked_then_it_is_nought_in_that_currency() {
        assertThat(Money.zero("EUR")).isEqualTo(Money.of("EUR", "0.00"));
    }

    @Test
    void given_a_blank_currency_when_constructed_then_it_refuses() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of("  ", "1.00"));
    }

    @Test
    void given_no_amount_when_constructed_then_it_refuses() {
        assertThatNullPointerException().isThrownBy(() -> Money.of("CHF", (BigDecimal) null));
    }
}
