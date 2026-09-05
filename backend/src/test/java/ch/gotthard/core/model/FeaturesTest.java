package ch.gotthard.core.model;

import static ch.gotthard.core.Fixtures.activity;
import static ch.gotthard.core.Fixtures.chf;
import static ch.gotthard.core.Fixtures.quiet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FeaturesTest {

    private static final CardSignals CARD = new CardSignals(4, 3, 2, chf("0.00"));

    @Test
    void given_a_card_activity_when_built_as_card_features_then_the_other_channels_are_empty() {
        final Features features = Features.card(activity(ActivityType.CARD), quiet(), CARD);

        assertThat(features.card()).isEqualTo(CARD);
        assertThat(features.payment()).isEqualTo(PaymentSignals.none("CHF"));
        assertThat(features.crypto()).isEqualTo(CryptoSignals.none("CHF"));
    }

    @Test
    void given_a_card_activity_when_built_as_card_features_then_empty_signals_carry_its_currency() {
        final Activity template = activity(ActivityType.CARD);
        final Activity inEuros = new Activity(
                template.transactionId(),
                template.customerId(),
                ActivityType.CARD,
                Money.of("EUR", "80.00"),
                template.occurredAt());

        final Features features = Features.card(inEuros, Velocity.none("EUR"), CardSignals.none("EUR"));

        assertThat(features.crypto().outboundVolume24h().currency()).isEqualTo("EUR");
    }

    @Test
    void given_a_payment_activity_when_built_as_card_features_then_it_refuses() {
        final Activity payment = activity(ActivityType.PAYMENT);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Features.card(payment, quiet(), CARD))
                .withMessageContaining("expected a CARD activity");
    }

    @Test
    void given_a_crypto_activity_when_built_as_crypto_features_then_it_reports_its_channel() {
        final Features features = Features.crypto(activity(ActivityType.CRYPTO), quiet(), CryptoSignals.none("CHF"));

        assertThat(features.activityType()).isEqualTo(ActivityType.CRYPTO);
    }

    @Test
    void given_a_payment_activity_when_built_as_payment_features_then_the_velocity_is_kept() {
        final Velocity velocity = new Velocity(chf("500.00"), chf("38000.00"), 3, 4, Duration.ofDays(100));

        final Features features =
                Features.payment(activity(ActivityType.PAYMENT), velocity, PaymentSignals.none("CHF"));

        assertThat(features.velocity()).isEqualTo(velocity);
    }

    @Test
    void given_missing_signals_when_constructed_then_it_refuses() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Features(
                        activity(ActivityType.CARD),
                        quiet(),
                        null,
                        PaymentSignals.none("CHF"),
                        CryptoSignals.none("CHF")));
    }
}
