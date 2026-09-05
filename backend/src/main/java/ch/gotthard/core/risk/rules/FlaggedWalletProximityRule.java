package ch.gotthard.core.risk.rules;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.risk.Rule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * R-05 — proximity to a flagged wallet. The sending wallet sits within two hops of an address on the
 * watch list, counted by the depth-limited walk of the wallet graph.
 *
 * <p>Distance is the whole signal, so this rule grades itself: dealing directly with a flagged
 * address is worth the full weight, and being one wallet removed is worth half of it. Anything
 * further out, or unreachable within the search depth, does not fire at all.
 */
public final class FlaggedWalletProximityRule implements Rule {

    public static final String CODE = "R-05";

    /** Beyond two hops the graph connects almost everything to almost everything. */
    public static final int MAX_HOPS = 2;

    /** Sending straight to, or receiving straight from, the flagged address. */
    public static final int DIRECT_HOPS = 1;

    /** What one intermediary wallet between the two is worth. */
    public static final BigDecimal INDIRECT_FACTOR = new BigDecimal("0.50");

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<ActivityType> appliesTo() {
        return Set.of(ActivityType.CRYPTO);
    }

    @Override
    public boolean fires(final Features features) {
        return features.crypto().isWithinHopsOfFlaggedWallet(MAX_HOPS);
    }

    @Override
    public BigDecimal contribution(final Features features, final BigDecimal weight) {
        return weight.multiply(proximityFactor(features.crypto().hopsToFlaggedWallet()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal proximityFactor(final int hops) {
        return hops <= DIRECT_HOPS ? BigDecimal.ONE : INDIRECT_FACTOR;
    }
}
