package ch.gotthard.core.graph;

import ch.gotthard.core.model.CryptoSignals;
import ch.gotthard.core.model.Require;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How far a customer's wallets sit from the watch list, in hops.
 *
 * <p>A breadth-first search of the wallet graph, run one frontier at a time from every source
 * wallet at once so the first frontier that contains a flagged address is, by construction, the
 * nearest one — not merely the first path the walk happened to follow. A wallet already on the
 * watch list costs nothing to detect: it is checked before the first hop is taken, so it is
 * always zero hops away.
 *
 * <p>The walk never runs past {@code maxDepth}: the cap bounds the frontier expansion itself,
 * so a flagged address one hop beyond it is exactly as invisible as one that is unreachable.
 * {@link CryptoSignals#NO_PATH_TO_FLAGGED_WALLET} covers both cases identically, which is the
 * point of that sentinel — the rule reading it does not need to know which one happened.
 */
public final class FlaggedWalletSearch {

    private final WalletGraph graph;

    public FlaggedWalletSearch(final WalletGraph graph) {
        this.graph = Require.present(graph, "graph");
    }

    /**
     * Hops from the nearest of {@code sourceWallets} to the nearest of {@code flaggedWallets},
     * capped at {@code maxDepth}. {@link CryptoSignals#NO_PATH_TO_FLAGGED_WALLET} if none of the
     * flagged wallets is reachable within the cap.
     */
    public int hopsToNearestFlaggedWallet(
            final Set<String> sourceWallets, final Set<String> flaggedWallets, final int maxDepth) {
        Require.present(sourceWallets, "sourceWallets");
        Require.present(flaggedWallets, "flaggedWallets");
        Require.notNegative(maxDepth, "maxDepth");

        Set<String> frontier = Set.copyOf(sourceWallets);
        if (reaches(frontier, flaggedWallets)) {
            return 0;
        }

        final Set<String> visited = new HashSet<>(sourceWallets);
        for (int depth = 1; depth <= maxDepth; depth++) {
            frontier = expand(frontier, visited);
            if (frontier.isEmpty()) {
                break;
            }
            if (reaches(frontier, flaggedWallets)) {
                return depth;
            }
        }
        return CryptoSignals.NO_PATH_TO_FLAGGED_WALLET;
    }

    private static boolean reaches(final Set<String> frontier, final Set<String> flaggedWallets) {
        return frontier.stream().anyMatch(flaggedWallets::contains);
    }

    /** One layer out: every wallet reachable in exactly one more hop from {@code frontier} that is new. */
    private Set<String> expand(final Set<String> frontier, final Set<String> visited) {
        return frontier.stream()
                .flatMap(wallet -> graph.neighborsOf(wallet).stream())
                .filter(visited::add)
                .collect(Collectors.toUnmodifiableSet());
    }
}
