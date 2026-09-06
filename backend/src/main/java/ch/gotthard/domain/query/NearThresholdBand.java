package ch.gotthard.domain.query;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The band of amounts that counts as "just below the reporting threshold".
 *
 * <p>Structuring is not a large amount, it is a deliberately unremarkable one: 9 500 rather than
 * 10 000, four times over. Where that band starts is policy, so it is passed in rather than written
 * into the SQL — the query only asks whether an amount fell inside it.
 *
 * @param floor inclusive lower bound of the band
 * @param threshold exclusive upper bound — the reporting threshold itself, which an amount at or
 *     above is no longer hiding from
 */
public record NearThresholdBand(BigDecimal floor, BigDecimal threshold) {

    /** How far below the threshold still reads as deliberate. Nine tenths, i.e. 9 000 of 10 000. */
    public static final BigDecimal DEFAULT_FLOOR_FRACTION = new BigDecimal("0.90");

    public NearThresholdBand {
        Objects.requireNonNull(floor, "floor is required");
        Objects.requireNonNull(threshold, "threshold is required");
        if (floor.compareTo(threshold) >= 0) {
            throw new IllegalArgumentException("floor " + floor + " must sit below threshold " + threshold);
        }
    }

    /** The default band below a reporting threshold: {@value #DEFAULT_FLOOR_FRACTION} of it, up to it. */
    public static NearThresholdBand below(final BigDecimal threshold) {
        return new NearThresholdBand(
                Objects.requireNonNull(threshold, "threshold is required")
                        .multiply(DEFAULT_FLOOR_FRACTION)
                        .setScale(2, RoundingMode.HALF_UP),
                threshold);
    }
}
