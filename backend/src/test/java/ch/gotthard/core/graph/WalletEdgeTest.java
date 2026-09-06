package ch.gotthard.core.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class WalletEdgeTest {

    @Test
    void given_a_pair_of_addresses_when_constructed_then_the_endpoints_are_retained() {
        final WalletEdge edge = new WalletEdge("0xAAA", "0xBBB");

        assertThat(edge.from()).isEqualTo("0xAAA");
        assertThat(edge.to()).isEqualTo("0xBBB");
    }

    @Test
    void given_mixed_case_addresses_when_constructed_then_the_case_is_preserved() {
        final WalletEdge edge = new WalletEdge("0xAbCdEf", "0xFeDcBa");

        assertThat(edge.from()).isEqualTo("0xAbCdEf");
        assertThat(edge.to()).isEqualTo("0xFeDcBa");
    }

    @Test
    void given_a_blank_from_address_when_constructed_then_it_refuses() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WalletEdge("   ", "0xBBB"))
                .withMessageContaining("from");
    }

    @Test
    void given_a_blank_to_address_when_constructed_then_it_refuses() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WalletEdge("0xAAA", "   "))
                .withMessageContaining("to");
    }

    @Test
    void given_a_null_from_address_when_constructed_then_it_refuses() {
        assertThatNullPointerException().isThrownBy(() -> new WalletEdge(null, "0xBBB"));
    }

    @Test
    void given_a_null_to_address_when_constructed_then_it_refuses() {
        assertThatNullPointerException().isThrownBy(() -> new WalletEdge("0xAAA", null));
    }
}
