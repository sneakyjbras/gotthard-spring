package ch.gotthard.core.model;

import static ch.gotthard.core.Fixtures.chf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CryptoSignalsTest {

    @Test
    void given_a_wallet_two_hops_from_a_flagged_one_when_asked_about_two_hops_then_it_is_within_reach() {
        final CryptoSignals signals = new CryptoSignals(false, Duration.ofHours(3), chf("0.00"), 2);

        assertThat(signals.isWithinHopsOfFlaggedWallet(2)).isTrue();
    }

    @Test
    void given_a_wallet_three_hops_from_a_flagged_one_when_asked_about_two_hops_then_it_is_out_of_reach() {
        final CryptoSignals signals = new CryptoSignals(false, Duration.ofHours(3), chf("0.00"), 3);

        assertThat(signals.isWithinHopsOfFlaggedWallet(2)).isFalse();
    }

    @Test
    void given_no_flagged_wallet_within_the_search_depth_when_asked_then_no_hop_count_reaches_it() {
        final CryptoSignals signals =
                new CryptoSignals(false, Duration.ofHours(3), chf("0.00"), CryptoSignals.NO_PATH_TO_FLAGGED_WALLET);

        assertThat(signals.isWithinHopsOfFlaggedWallet(Integer.MAX_VALUE - 1)).isFalse();
    }

    @Test
    void given_funds_moved_on_within_the_hour_when_asked_about_an_hour_then_the_move_was_that_rapid() {
        final CryptoSignals signals = new CryptoSignals(true, Duration.ofMinutes(12), chf("20000.00"), 9);

        assertThat(signals.movedWithin(Duration.ofHours(1))).isTrue();
    }

    @Test
    void given_no_inbound_funding_observed_when_asked_about_any_window_then_the_move_was_not_rapid() {
        final CryptoSignals signals = new CryptoSignals(
                true, CryptoSignals.NO_INBOUND_FUNDING, chf("20000.00"), CryptoSignals.NO_PATH_TO_FLAGGED_WALLET);

        assertThat(signals.movedWithin(Duration.ofDays(3650))).isFalse();
    }

    @Test
    void given_a_negative_funding_gap_when_constructed_then_it_refuses() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CryptoSignals(true, Duration.ofHours(-1), chf("0.00"), 1))
                .withMessageContaining("sinceInboundFunding");
    }

    @Test
    void given_a_transaction_on_another_channel_when_asked_for_crypto_signals_then_nothing_is_reachable() {
        final CryptoSignals none = CryptoSignals.none("CHF");

        assertThat(none.destinationIsExchange()).isFalse();
        assertThat(none.isWithinHopsOfFlaggedWallet(2)).isFalse();
        assertThat(none.movedWithin(Duration.ofHours(1))).isFalse();
    }
}
