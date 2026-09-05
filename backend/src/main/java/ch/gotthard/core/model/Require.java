package ch.gotthard.core.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * The validation vocabulary the core value objects share.
 *
 * <p>Every record here refuses nulls and nonsense in its compact constructor. Without a shared
 * vocabulary each of those constructors grows a block of {@code if} statements; with one they stay a
 * line per component and read as a statement of the invariant.
 */
public final class Require {

    private Require() {}

    /** The value itself, or a {@link NullPointerException} naming the component that was missing. */
    public static <T> T present(final T value, final String name) {
        return Objects.requireNonNull(value, name + " is required");
    }

    /** Trimmed text, rejecting blanks — an empty name is a missing name, not a value. */
    public static String text(final String value, final String name) {
        final String trimmed = present(value, name).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }

    /** Text normalised to upper case, for identifiers the database compares case-insensitively. */
    public static String code(final String value, final String name) {
        return text(value, name).toUpperCase(Locale.ROOT);
    }

    /** A count of things that happened. A negative count is a bug in the query that produced it. */
    public static int notNegative(final int value, final String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative, was " + value);
        }
        return value;
    }

    /** An elapsed time. Time does not run backwards, so neither does a gap between two activities. */
    public static Duration notNegative(final Duration value, final String name) {
        if (present(value, name).isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative, was " + value);
        }
        return value;
    }

    /** A weight or a score contribution. Risk only ever adds up. */
    public static BigDecimal notNegative(final BigDecimal value, final String name) {
        if (present(value, name).signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative, was " + value);
        }
        return value;
    }
}
