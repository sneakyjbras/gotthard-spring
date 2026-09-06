package ch.gotthard.service;

import ch.gotthard.core.model.CryptoSignals;

/**
 * How many hops separate a wallet from the nearest flagged address.
 *
 * <p>The one signal on {@link ch.gotthard.core.model.Features} that no window function can produce:
 * it is a walk of the wallet graph, not an aggregate over a timeline. Stating it as a port here lets
 * the assembler be complete today and lets the graph search be plugged in when it lands, without
 * either side knowing about the other.
 *
 * <p>The default answers honestly rather than optimistically: not knowing whether a wallet is near a
 * flagged address is not the same as knowing it is not, and {@link
 * CryptoSignals#NO_PATH_TO_FLAGGED_WALLET} is the value the core already reserves for "the search
 * did not reach one".
 */
@FunctionalInterface
public interface WalletProximity {

    /** Edges between this wallet and the nearest flagged one. */
    int hopsFrom(String wallet);

    /** No graph search wired in — every wallet reads as unreachable. */
    static WalletProximity unknown() {
        return wallet -> CryptoSignals.NO_PATH_TO_FLAGGED_WALLET;
    }
}
