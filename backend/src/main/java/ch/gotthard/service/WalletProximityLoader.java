package ch.gotthard.service;

import ch.gotthard.core.graph.FlaggedWalletSearch;
import ch.gotthard.core.graph.WalletEdge;
import ch.gotthard.core.graph.WalletGraph;
import ch.gotthard.core.risk.rules.FlaggedWalletProximityRule;
import ch.gotthard.domain.query.WalletEdgeRow;
import ch.gotthard.domain.query.WalletGraphQuery;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands the wallet graph to the search that walks it.
 *
 * <p>The seam for the one {@code Features} signal no window function can produce. {@code
 * crypto_activity} holds the edges, {@link FlaggedWalletSearch} holds the algorithm, and neither
 * knows about the other: the search is pure Java that takes an edge set, this class is the only
 * thing that knows the edge set lives in a table.
 *
 * <p>The graph is built once per evaluation, not once per transaction — a customer's crypto
 * transfers all walk the same network, and rebuilding it for each of them would turn one query into
 * as many as there are transfers.
 */
@Component
public class WalletProximityLoader {

    private final WalletGraphQuery edges;
    private final FlaggedWallets flaggedWallets;

    public WalletProximityLoader(final WalletGraphQuery edges, final FlaggedWallets flaggedWallets) {
        this.edges = edges;
        this.flaggedWallets = flaggedWallets;
    }

    /**
     * A proximity measure over the current graph. With nothing on the watch list there is nothing to
     * be near, so the graph is never loaded at all.
     */
    @Transactional(readOnly = true)
    public WalletProximity load() {
        final Set<String> watchList = flaggedWallets.addresses();
        return watchList.isEmpty() ? WalletProximity.unknown() : searchOver(watchList);
    }

    private WalletProximity searchOver(final Set<String> watchList) {
        final FlaggedWalletSearch search = new FlaggedWalletSearch(WalletGraph.build(walletEdges()));
        return wallet ->
                search.hopsToNearestFlaggedWallet(Set.of(wallet), watchList, FlaggedWalletProximityRule.MAX_HOPS);
    }

    private List<WalletEdge> walletEdges() {
        return edges.findAllEdges().stream().map(WalletProximityLoader::edgeOf).toList();
    }

    private static WalletEdge edgeOf(final WalletEdgeRow row) {
        return new WalletEdge(row.from(), row.to());
    }
}
