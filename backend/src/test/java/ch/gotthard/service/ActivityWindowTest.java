package ch.gotthard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** An operator who names no window means "recently", and that has to become two instants. */
class ActivityWindowTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-03-01T00:00:00Z");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-03-15T12:00:00Z");

    @Test
    void given_bothEnds_when_between_then_theyAreKeptAsGiven() {
        final ActivityWindow window = ActivityWindow.between(Optional.of(START), Optional.of(END));

        assertThat(window.from()).isEqualTo(START);
        assertThat(window.to()).isEqualTo(END);
    }

    @Test
    void given_onlyTheEnd_when_between_then_theStartIsTheDefaultLookbackBeforeIt() {
        final ActivityWindow window = ActivityWindow.between(Optional.empty(), Optional.of(END));

        assertThat(window.from()).isEqualTo(END.minus(ActivityWindow.DEFAULT_LOOKBACK));
        assertThat(window.to()).isEqualTo(END);
    }

    @Test
    void given_neitherEnd_when_between_then_theWindowIsTheDefaultLookbackEndingNow() {
        final ActivityWindow window = ActivityWindow.between(Optional.empty(), Optional.empty());

        assertThat(window.from()).isEqualTo(window.to().minus(ActivityWindow.DEFAULT_LOOKBACK));
        assertThat(window.to()).isAfterOrEqualTo(OffsetDateTime.now().minusMinutes(1));
    }

    /** A window that ends before it starts is a typed request, not an empty result. */
    @Test
    void given_anEndBeforeTheStart_when_between_then_itIsRefused() {
        assertThatThrownBy(() -> ActivityWindow.between(Optional.of(END), Optional.of(START)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
