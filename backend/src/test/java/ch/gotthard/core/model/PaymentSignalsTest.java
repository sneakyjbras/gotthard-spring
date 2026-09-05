package ch.gotthard.core.model;

import static ch.gotthard.core.Fixtures.chf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PaymentSignalsTest {

    @Test
    void given_country_codes_in_mixed_case_when_constructed_then_they_are_normalised() {
        final PaymentSignals signals = new PaymentSignals(Set.of("ch", "De"), 1, chf("100.00"));

        assertThat(signals.counterpartyCountries7d()).containsExactlyInAnyOrder("CH", "DE");
    }

    @Test
    void given_a_country_code_of_the_wrong_length_when_constructed_then_it_refuses() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PaymentSignals(Set.of("CHE"), 1, chf("100.00")))
                .withMessageContaining("ISO-3166 alpha-2");
    }

    @Test
    void given_a_corridor_country_on_a_watch_list_when_asked_then_it_reports_the_touch() {
        final PaymentSignals signals = new PaymentSignals(Set.of("CH", "IR"), 2, chf("100.00"));

        assertThat(signals.touches(Set.of("IR", "KP"))).isTrue();
    }

    @Test
    void given_no_corridor_country_on_a_watch_list_when_asked_then_it_reports_no_touch() {
        final PaymentSignals signals = new PaymentSignals(Set.of("CH", "DE"), 2, chf("100.00"));

        assertThat(signals.touches(Set.of("IR", "KP"))).isFalse();
    }

    @Test
    void given_the_countries_when_the_source_set_is_changed_afterwards_then_the_record_is_unaffected() {
        final Set<String> mutable = new HashSet<>(Set.of("CH"));
        final PaymentSignals signals = new PaymentSignals(mutable, 1, chf("100.00"));

        mutable.add("IR");

        assertThat(signals.counterpartyCountries7d()).containsExactly("CH");
    }

    @Test
    void given_a_transaction_on_another_channel_when_asked_for_payment_signals_then_they_are_empty() {
        final PaymentSignals none = PaymentSignals.none("CHF");

        assertThat(none.counterpartyCountries7d()).isEmpty();
        assertThat(none.crossBorderCount7d()).isZero();
        assertThat(none.crossBorderVolume7d()).isEqualTo(chf("0.00"));
    }
}
