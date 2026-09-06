package ch.gotthard.domain.query;

/**
 * One directed transfer between two wallets, straight off {@code crypto_activity}.
 *
 * <p>Two strings and no vocabulary: the graph search has its own edge type and its own opinions
 * about blank addresses, and translating into it is the service layer's job — the same seam that
 * keeps {@link FeatureRow} free of the risk core.
 */
public record WalletEdgeRow(String from, String to) {}
