package ch.gotthard.core.graph;

import ch.gotthard.core.model.Require;

/**
 * One directed transfer between two wallets — an edge in the wallet graph.
 *
 * <p>Mirrors one {@code crypto_activity} row: {@code wallet_address_from} to {@code
 * wallet_address_to}. Nothing here loads those rows; the edge set is handed to {@link
 * WalletGraph#build} by whatever already queried them.
 *
 * @param from the sending wallet address
 * @param to the receiving wallet address
 */
public record WalletEdge(String from, String to) {

    public WalletEdge {
        // Addresses are case-sensitive on most chains, so this trims and rejects blanks without
        // normalising case the way Require.code would for a database identifier.
        from = Require.text(from, "from");
        to = Require.text(to, "to");
    }
}
