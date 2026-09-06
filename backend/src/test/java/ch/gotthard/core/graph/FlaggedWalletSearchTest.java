package ch.gotthard.core.graph;

import static ch.gotthard.core.model.CryptoSignals.NO_PATH_TO_FLAGGED_WALLET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FlaggedWalletSearchTest {

    @Test
    void given_a_source_wallet_that_is_itself_flagged_when_searched_then_it_is_zero_hops() {
        final FlaggedWalletSearch search = searching(new WalletEdge("A", "B"));

        final int hops = search.hopsToNearestFlaggedWallet(Set.of("A"), Set.of("A"), 2);

        assertThat(hops).isZero();
    }

    @Test
    void given_a_flagged_wallet_beyond_the_depth_cap_when_searched_then_the_cap_is_respected() {
        final FlaggedWalletSearch search =
                searching(new WalletEdge("A", "B"), new WalletEdge("B", "C"), new WalletEdge("C", "D"));

        final int hops = search.hopsToNearestFlaggedWallet(Set.of("A"), Set.of("D"), 2);

        assertThat(hops).isEqualTo(NO_PATH_TO_FLAGGED_WALLET);
    }

    @Test
    void given_a_flagged_wallet_exactly_at_the_depth_cap_when_searched_then_it_is_still_found() {
        final FlaggedWalletSearch search =
                searching(new WalletEdge("A", "B"), new WalletEdge("B", "C"), new WalletEdge("C", "D"));

        final int hops = search.hopsToNearestFlaggedWallet(Set.of("A"), Set.of("D"), 3);

        assertThat(hops).isEqualTo(3);
    }

    @Test
    void given_a_shorter_and_a_longer_route_to_the_same_flagged_wallet_when_searched_then_the_shorter_one_wins() {
        final FlaggedWalletSearch search = searching(
                new WalletEdge("A", "X"),
                new WalletEdge("X", "Z"),
                new WalletEdge("A", "P"),
                new WalletEdge("P", "Q"),
                new WalletEdge("Q", "R"),
                new WalletEdge("R", "Z"));

        final int hops = search.hopsToNearestFlaggedWallet(Set.of("A"), Set.of("Z"), 4);

        assertThat(hops).isEqualTo(2);
    }

    @Test
    void given_several_source_wallets_when_one_sits_nearer_a_flagged_wallet_then_the_nearest_distance_wins() {
        final FlaggedWalletSearch search =
                searching(new WalletEdge("A", "M"), new WalletEdge("M", "Z"), new WalletEdge("B", "Z"));

        final int hops = search.hopsToNearestFlaggedWallet(Set.of("A", "B"), Set.of("Z"), 3);

        assertThat(hops).isEqualTo(1);
    }

    @Test
    void given_a_graph_with_no_path_between_the_components_when_searched_then_the_sentinel_is_returned() {
        final FlaggedWalletSearch search = searching(new WalletEdge("A", "B"), new WalletEdge("C", "D"));

        final int hops = search.hopsToNearestFlaggedWallet(Set.of("A"), Set.of("D"), 10);

        assertThat(hops).isEqualTo(NO_PATH_TO_FLAGGED_WALLET);
    }

    @Test
    void given_a_cycle_with_no_flagged_wallet_reachable_when_searched_then_it_terminates() {
        final FlaggedWalletSearch search =
                searching(new WalletEdge("A", "B"), new WalletEdge("B", "C"), new WalletEdge("C", "A"));

        final int hops = assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> search.hopsToNearestFlaggedWallet(Set.of("A"), Set.of("nowhere-in-the-cycle"), 50));

        assertThat(hops).isEqualTo(NO_PATH_TO_FLAGGED_WALLET);
    }

    @Test
    void given_a_negative_depth_cap_when_searched_then_it_refuses() {
        final FlaggedWalletSearch search = searching(new WalletEdge("A", "B"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> search.hopsToNearestFlaggedWallet(Set.of("A"), Set.of("B"), -1));
    }

    @Test
    void given_a_null_source_wallets_set_when_searched_then_it_refuses() {
        final FlaggedWalletSearch search = searching(new WalletEdge("A", "B"));

        assertThatNullPointerException().isThrownBy(() -> search.hopsToNearestFlaggedWallet(null, Set.of("B"), 2));
    }

    @Test
    void given_a_null_flagged_wallets_set_when_searched_then_it_refuses() {
        final FlaggedWalletSearch search = searching(new WalletEdge("A", "B"));

        assertThatNullPointerException().isThrownBy(() -> search.hopsToNearestFlaggedWallet(Set.of("A"), null, 2));
    }

    @Test
    void given_a_null_graph_when_constructing_a_search_then_it_refuses() {
        assertThatNullPointerException().isThrownBy(() -> new FlaggedWalletSearch(null));
    }

    @Test
    void given_a_wide_graph_far_larger_than_the_cap_when_searched_then_the_cap_bounds_the_work_not_just_the_answer() {
        final GeneratedTree tree = wideTree(50, 3);
        // 50 + 2,500 + 125,000 edges: a graph a full, uncapped walk would have to cross entirely.
        assertThat(tree.edges()).hasSize(127_550);

        final FlaggedWalletSearch search = new FlaggedWalletSearch(WalletGraph.build(tree.edges()));
        final String threeHopsOut = tree.deepestLevel().get(0);

        final int hops = assertTimeoutPreemptively(
                Duration.ofMillis(500),
                () -> search.hopsToNearestFlaggedWallet(Set.of("root"), Set.of(threeHopsOut), 1));

        // At maxDepth 1 the walk can only ever have visited root and its 50 direct children — 51
        // of the 127,551 wallets in the graph — so it returns almost instantly despite the flagged
        // wallet sitting three hops out. That is the cap bounding the traversal itself, not merely
        // the value the traversal happens to return.
        assertThat(hops).isEqualTo(NO_PATH_TO_FLAGGED_WALLET);
    }

    private static FlaggedWalletSearch searching(final WalletEdge... edges) {
        return new FlaggedWalletSearch(WalletGraph.build(List.of(edges)));
    }

    /** A tree with {@code branchingFactor} children per node, {@code levels} deep below {@code "root"}. */
    private static GeneratedTree wideTree(final int branchingFactor, final int levels) {
        List<String> currentLevel = List.of("root");
        final List<WalletEdge> edges = new ArrayList<>();
        for (int level = 0; level < levels; level++) {
            final List<String> nextLevel = new ArrayList<>();
            for (final String parent : currentLevel) {
                for (int child = 0; child < branchingFactor; child++) {
                    final String address = parent + "-" + child;
                    edges.add(new WalletEdge(parent, address));
                    nextLevel.add(address);
                }
            }
            currentLevel = nextLevel;
        }
        return new GeneratedTree(edges, currentLevel);
    }

    private record GeneratedTree(List<WalletEdge> edges, List<String> deepestLevel) {}
}
