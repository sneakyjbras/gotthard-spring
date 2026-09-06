package ch.gotthard.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.core.model.CryptoSignals;
import ch.gotthard.domain.query.WalletGraphQuery;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The one signal that is a graph walk rather than a window: {@code crypto_activity} to edge set to
 * breadth-first search, with the watch list supplied from configuration.
 *
 * <p>The fixture is a two-hop chain — W1 paid W2, W2 paid a flagged address — built out of two
 * transfers belonging to two different customers, because a chain that only counts when one customer
 * owns all of it is not a chain.
 */
class WalletProximityLoaderTest extends AbstractServiceIntegrationTest {

    private static final OffsetDateTime ANCHOR = OffsetDateTime.parse("2026-03-15T12:00:00Z");
    private static final String FLAGGED = "wpl-FLAGGED";

    @Autowired
    private WalletGraphQuery walletGraph;

    @Test
    void given_aTwoHopChainToAFlaggedWallet_when_load_then_theDistanceIsTwoHops() {
        seedChain();

        final WalletProximity proximity = loaderWatching(FLAGGED).load();

        assertThat(proximity.hopsFrom("wpl-W1")).isEqualTo(2);
        assertThat(proximity.hopsFrom("wpl-W2")).isEqualTo(1);
    }

    @Test
    void given_aWalletOffTheChain_when_load_then_noFlaggedWalletIsReachable() {
        seedChain();

        assertThat(loaderWatching(FLAGGED).load().hopsFrom("wpl-W9"))
                .isEqualTo(CryptoSignals.NO_PATH_TO_FLAGGED_WALLET);
    }

    /** With nothing on the watch list there is nothing to be near, and the graph is never loaded. */
    @Test
    void given_noWatchList_when_load_then_everyWalletIsUnreachable() {
        seedChain();

        assertThat(loaderWatching("").load().hopsFrom("wpl-W1")).isEqualTo(CryptoSignals.NO_PATH_TO_FLAGGED_WALLET);
    }

    private WalletProximityLoader loaderWatching(final String configured) {
        return new WalletProximityLoader(walletGraph, new FlaggedWallets(configured));
    }

    private void seedChain() {
        final UUID customer = fixtures.customer("CH");
        final UUID counterparty = fixtures.customer("CH");
        fixtures.crypto(customer, ANCHOR.minusHours(3), "100.00", "wpl-W1", "wpl-W2", null);
        fixtures.crypto(counterparty, ANCHOR.minusHours(1), "80.00", "wpl-W2", FLAGGED, null);
    }
}
