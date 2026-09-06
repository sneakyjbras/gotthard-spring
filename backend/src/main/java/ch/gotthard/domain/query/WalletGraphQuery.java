package ch.gotthard.domain.query;

import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The wallet graph's edge set.
 *
 * <p>Distinct pairs rather than one row per transfer: a wallet that paid another a hundred times is
 * still one edge, and the search only ever asks who a wallet sent to. Deduplicating in PostgreSQL
 * keeps the graph the size of the network rather than the size of the ledger.
 *
 * <p>The whole edge set is loaded, not a neighbourhood of the customer's wallets. Bounding the load
 * would mean expanding hop by hop in SQL — which is the breadth-first search itself, rewritten as a
 * recursive CTE, in the one layer that is not supposed to contain the algorithm. At this scale the
 * table is small; a deployment where it is not would page the graph in behind this same method,
 * without anything above it changing.
 */
@Repository
public class WalletGraphQuery {

    private static final String SQL =
            """
            SELECT DISTINCT wallet_address_from, wallet_address_to
            FROM crypto_activity""";

    private final NamedParameterJdbcTemplate jdbc;

    public WalletGraphQuery(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WalletEdgeRow> findAllEdges() {
        return jdbc.query(
                SQL,
                (rs, rowNumber) ->
                        new WalletEdgeRow(rs.getString("wallet_address_from"), rs.getString("wallet_address_to")));
    }
}
