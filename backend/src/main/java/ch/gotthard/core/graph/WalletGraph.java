package ch.gotthard.core.graph;

import ch.gotthard.core.model.Require;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The wallet graph, indexed for one question: who did this wallet send to?
 *
 * <p>Built once from the full edge set — every {@code crypto_activity} row translated to a {@link
 * WalletEdge} — and read many times after that, so construction groups every edge by its sending
 * wallet up front rather than scanning the edge list on each lookup a search makes.
 *
 * <p>A wallet with no recorded outgoing transfers has no neighbors, whether it never appears in
 * the edge set at all or only ever appears as a receiver. The graph does not distinguish those
 * two cases because a depth-limited search treats them identically either way: a dead end.
 */
public final class WalletGraph {

    private final Map<String, Set<String>> outgoing;

    private WalletGraph(final Map<String, Set<String>> outgoing) {
        this.outgoing = outgoing;
    }

    /** Groups the edges by sending wallet. The edge set is the whole graph; nothing is loaded here. */
    public static WalletGraph build(final Collection<WalletEdge> edges) {
        final List<WalletEdge> copy = List.copyOf(Require.present(edges, "edges"));
        final Map<String, Set<String>> outgoing = copy.stream()
                .collect(Collectors.groupingBy(
                        WalletEdge::from, Collectors.mapping(WalletEdge::to, Collectors.toUnmodifiableSet())));
        return new WalletGraph(Map.copyOf(outgoing));
    }

    /** The wallets this one sent to directly, or an empty set if it never appears as a sender. */
    public Set<String> neighborsOf(final String wallet) {
        return outgoing.getOrDefault(Require.text(wallet, "wallet"), Set.of());
    }
}
