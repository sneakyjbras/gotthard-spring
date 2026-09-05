package ch.gotthard.core.model;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * What the wallet graph and the clock say about this crypto transfer.
 *
 * <p>Two components describe something that may not exist — funding that was never seen, a flagged
 * wallet the graph search never reached. Both are given a value that is honest about it rather than
 * an {@code Optional} field: a gap of forever, and a distance beyond any depth limit. Rules compare
 * against them without a special case, which is the point.
 *
 * @param destinationIsExchange the receiving wallet belongs to a known exchange, so this is the leg
 *     where crypto turns back into money
 * @param sinceInboundFunding gap between funds arriving at the sending wallet and this transfer
 *     leaving it, {@link #NO_INBOUND_FUNDING} when no funding was observed
 * @param outboundVolume24h what left the sending wallet over the last twenty-four hours
 * @param hopsToFlaggedWallet edges between the sending wallet and the nearest flagged one, {@link
 *     #NO_PATH_TO_FLAGGED_WALLET} when the depth-limited search found none
 */
public record CryptoSignals(
        boolean destinationIsExchange, Duration sinceInboundFunding, Money outboundVolume24h, int hopsToFlaggedWallet) {

    /** No flagged wallet within the search depth. Larger than any hop count a rule will accept. */
    public static final int NO_PATH_TO_FLAGGED_WALLET = Integer.MAX_VALUE;

    /** No inbound funding observed. Longer than any window a rule will call rapid. */
    public static final Duration NO_INBOUND_FUNDING = ChronoUnit.FOREVER.getDuration();

    public CryptoSignals {
        Require.notNegative(sinceInboundFunding, "sinceInboundFunding");
        Require.present(outboundVolume24h, "outboundVolume24h");
        Require.notNegative(hopsToFlaggedWallet, "hopsToFlaggedWallet");
    }

    /** No crypto activity — what a card or payment transaction carries. */
    public static CryptoSignals none(final String currency) {
        return new CryptoSignals(false, NO_INBOUND_FUNDING, Money.zero(currency), NO_PATH_TO_FLAGGED_WALLET);
    }

    public boolean isWithinHopsOfFlaggedWallet(final int hops) {
        return hopsToFlaggedWallet <= Require.notNegative(hops, "hops");
    }

    public boolean movedWithin(final Duration window) {
        return sinceInboundFunding.compareTo(Require.notNegative(window, "window")) <= 0;
    }
}
