package ch.gotthard.core.model;

import static ch.gotthard.core.Fixtures.chf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class CardSignalsTest {

    @Test
    void given_more_card_not_present_declines_than_declines_when_constructed_then_it_refuses() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CardSignals(2, 3, 1, chf("0.00")))
                .withMessageContaining("exceed all declines");
    }

    @Test
    void given_a_negative_count_when_constructed_then_it_refuses() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CardSignals(-1, 0, 0, chf("0.00")))
                .withMessageContaining("declineCount1h");
    }

    @Test
    void given_a_transaction_on_another_channel_when_asked_for_card_signals_then_they_are_empty() {
        final CardSignals none = CardSignals.none("CHF");

        assertThat(none.declineCount1h()).isZero();
        assertThat(none.cardNotPresentDeclineCount1h()).isZero();
        assertThat(none.distinctMerchantCount1h()).isZero();
        assertThat(none.quasiCashVolume24h()).isEqualTo(chf("0.00"));
    }
}
