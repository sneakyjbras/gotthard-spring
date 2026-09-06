package ch.gotthard.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * The slice of a customer's timeline a use case is asked about.
 *
 * <p>Both ends are optional at the edge of the system and neither is optional here: an operator who
 * names no window means "recently", and that has to become two instants before any query runs. The
 * defaults are applied once, in {@link #between}, so every layer below reads the same two timestamps
 * and a report can state exactly what it covered.
 */
public record ActivityWindow(OffsetDateTime from, OffsetDateTime to) {

    /** What "recently" means when nobody says. Long enough to show a pattern, short enough to read. */
    public static final Duration DEFAULT_LOOKBACK = Duration.ofDays(30);

    public ActivityWindow {
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("window starts at " + from + ", after it ends at " + to);
        }
    }

    /** The window an operator asked for, with whichever end they left out filled in. */
    public static ActivityWindow between(final Optional<OffsetDateTime> from, final Optional<OffsetDateTime> to) {
        final OffsetDateTime end = to.orElseGet(OffsetDateTime::now);
        return new ActivityWindow(from.orElseGet(() -> end.minus(DEFAULT_LOOKBACK)), end);
    }
}
