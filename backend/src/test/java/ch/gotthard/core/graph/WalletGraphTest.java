package ch.gotthard.core.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import org.junit.jupiter.api.Test;

class WalletGraphTest {

    @Test
    void given_edges_when_built_then_each_wallets_neighbors_are_its_outgoing_transfers() {
        final WalletGraph graph = WalletGraph.build(
                List.of(new WalletEdge("A", "B"), new WalletEdge("A", "C"), new WalletEdge("B", "C")));

        assertThat(graph.neighborsOf("A")).containsExactlyInAnyOrder("B", "C");
        assertThat(graph.neighborsOf("B")).containsExactly("C");
    }

    @Test
    void given_a_wallet_that_only_ever_receives_when_asked_for_neighbors_then_it_has_none() {
        final WalletGraph graph = WalletGraph.build(List.of(new WalletEdge("A", "B")));

        assertThat(graph.neighborsOf("B")).isEmpty();
    }

    @Test
    void given_a_wallet_absent_from_every_edge_when_asked_for_neighbors_then_it_has_none() {
        final WalletGraph graph = WalletGraph.build(List.of(new WalletEdge("A", "B")));

        assertThat(graph.neighborsOf("nobody-ever-sent-or-received-here")).isEmpty();
    }

    @Test
    void given_the_same_transfer_recorded_twice_when_built_then_the_neighbor_is_not_duplicated() {
        final WalletGraph graph = WalletGraph.build(List.of(new WalletEdge("A", "B"), new WalletEdge("A", "B")));

        assertThat(graph.neighborsOf("A")).containsExactly("B");
    }

    @Test
    void given_no_edges_when_built_then_every_wallet_has_no_neighbors() {
        final WalletGraph graph = WalletGraph.build(List.of());

        assertThat(graph.neighborsOf("anything")).isEmpty();
    }

    @Test
    void given_a_null_edge_collection_when_built_then_it_refuses() {
        assertThatNullPointerException().isThrownBy(() -> WalletGraph.build(null));
    }

    @Test
    void given_a_null_wallet_when_asked_for_neighbors_then_it_refuses() {
        final WalletGraph graph = WalletGraph.build(List.of(new WalletEdge("A", "B")));

        assertThatNullPointerException().isThrownBy(() -> graph.neighborsOf(null));
    }
}
