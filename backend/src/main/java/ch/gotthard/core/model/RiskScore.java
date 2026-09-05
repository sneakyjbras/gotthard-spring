package ch.gotthard.core.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A risk score on a nought-to-a-hundred scale, and the band it falls into.
 *
 * <p>The canonical constructor is strict: a score off the scale is a programming error and says so.
 * {@link #of(BigDecimal)} is the forgiving door — summed rule contributions can overshoot a hundred
 * when enough rules fire at once, and a hundred is what "as bad as it gets" means.
 */
public record RiskScore(BigDecimal value) {

    public static final BigDecimal MINIMUM = new BigDecimal("0.00");
    public static final BigDecimal MAXIMUM = new BigDecimal("100.00");

    public RiskScore {
        value = Require.present(value, "value").setScale(Money.SCALE, RoundingMode.HALF_UP);
        if (value.compareTo(MINIMUM) < 0 || value.compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException("score outside 0..100: " + value);
        }
    }

    /** A score clamped onto the scale, for totals a long tail of firing rules pushed past it. */
    public static RiskScore of(final BigDecimal raw) {
        return new RiskScore(clamp(Require.present(raw, "raw")));
    }

    public static RiskScore zero() {
        return new RiskScore(MINIMUM);
    }

    public RiskLevel level() {
        return RiskLevel.forScore(value);
    }

    private static BigDecimal clamp(final BigDecimal raw) {
        return raw.max(MINIMUM).min(MAXIMUM);
    }
}
