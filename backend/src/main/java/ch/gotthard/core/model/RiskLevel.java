package ch.gotthard.core.model;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * The band a numeric score falls into.
 *
 * <p>The bands are the levels' own business: each constant carries the score at which it starts, and
 * the band a score belongs to is the highest one that has started by then. Written this way the
 * thresholds are a table anyone can read, a new band is a new constant, and nothing outside this
 * enum has to know a number.
 */
public enum RiskLevel {
    LOW("0.00"),
    MEDIUM("25.00"),
    HIGH("50.00"),
    CRITICAL("75.00");

    private final BigDecimal inclusiveLowerBound;

    RiskLevel(final String inclusiveLowerBound) {
        this.inclusiveLowerBound = new BigDecimal(inclusiveLowerBound);
    }

    /** The score at which this band opens. It runs up to the next band's bound, exclusive. */
    public BigDecimal inclusiveLowerBound() {
        return inclusiveLowerBound;
    }

    /** The highest band that has opened by the given score. */
    public static RiskLevel forScore(final BigDecimal score) {
        Require.notNegative(score, "score");
        return Arrays.stream(values())
                .filter(level -> level.opensAtOrBelow(score))
                .reduce((lower, higher) -> higher)
                .orElseThrow(() -> new IllegalStateException("no band opens at or below " + score));
    }

    private boolean opensAtOrBelow(final BigDecimal score) {
        return inclusiveLowerBound.compareTo(score) <= 0;
    }
}
