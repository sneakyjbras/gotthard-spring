package ch.gotthard.domain.query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * What a customer has been doing, for the screen an operator opens first.
 *
 * <p>Two statements rather than one: a grouped aggregate that PostgreSQL answers from {@code
 * idx_tx_customer_time} without materialising a row per transaction, and a small ordered page of the
 * transactions themselves. Totals across channels are a sum of three numbers and are added in Java —
 * a {@code GROUPING SETS} to save that would cost more to read than it saves to run.
 */
@Repository
public class ActivityOverviewQuery {

    private static final String CHANNEL_BREAKDOWN_SQL = "WITH " + SqlFragments.RATES_CTE
            + """

            SELECT t.activity_type,
                   COUNT(*) AS transaction_count,
                   COALESCE(SUM(%s), 0) AS volume,
                   COUNT(*) FILTER (WHERE t.status IN ('FAILED', 'REVERSED')) AS failed_count,
                   MIN(t.created_at) AS first_at,
                   MAX(t.created_at) AS last_at
            FROM transactions t
            LEFT JOIN rates r ON r.currency = t.currency
            WHERE t.customer_id = :customerId
              AND t.created_at >= :from
              AND t.created_at <= :to
            GROUP BY t.activity_type
            ORDER BY t.activity_type"""
                    .formatted(SqlFragments.REPORTING_AMOUNT);

    private static final String RECENT_ACTIVITY_SQL =
            """
            SELECT t.transaction_id,
                   t.activity_type,
                   t.amount,
                   t.currency,
                   t.status,
                   t.created_at,
                   COALESCE(pa.receiver_account, cr.wallet_address_to, ca.merchant_name) AS counterparty,
                   COALESCE(CAST(pa.receiver_bank_country AS text), cr.blockchain, CAST(ca.mcc_code AS text))
                       AS channel_detail
            FROM transactions t
            LEFT JOIN card_activity ca ON ca.transaction_id = t.transaction_id
            LEFT JOIN payment_activity pa ON pa.transaction_id = t.transaction_id
            LEFT JOIN crypto_activity cr ON cr.transaction_id = t.transaction_id
            WHERE t.customer_id = :customerId
              AND t.created_at >= :from
              AND t.created_at <= :to
            ORDER BY t.created_at DESC, t.transaction_id
            LIMIT :limit""";

    private final NamedParameterJdbcTemplate jdbc;

    public ActivityOverviewQuery(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One row per channel the customer actually used in the window; a silent channel is absent. */
    public List<ChannelActivityRow> findChannelBreakdown(
            final UUID customerId,
            final OffsetDateTime from,
            final OffsetDateTime to,
            final CurrencyConversion conversion) {
        return jdbc.query(
                CHANNEL_BREAKDOWN_SQL,
                window(customerId, from, to)
                        .addValue("rateCurrencies", conversion.currencyCodes())
                        .addValue("rateValues", conversion.rates()),
                channelMapper());
    }

    /** The newest transactions in the window, in the currency they happened in. */
    public List<RecentActivityRow> findRecentActivity(
            final UUID customerId, final OffsetDateTime from, final OffsetDateTime to, final int limit) {
        return jdbc.query(RECENT_ACTIVITY_SQL, window(customerId, from, to).addValue("limit", limit), recentMapper());
    }

    private static MapSqlParameterSource window(
            final UUID customerId, final OffsetDateTime from, final OffsetDateTime to) {
        return new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("from", from)
                .addValue("to", to);
    }

    private static RowMapper<ChannelActivityRow> channelMapper() {
        return (rs, rowNumber) -> new ChannelActivityRow(
                rs.getString("activity_type"),
                rs.getLong("transaction_count"),
                rs.getBigDecimal("volume"),
                rs.getLong("failed_count"),
                rs.getObject("first_at", OffsetDateTime.class),
                rs.getObject("last_at", OffsetDateTime.class));
    }

    private static RowMapper<RecentActivityRow> recentMapper() {
        return (rs, rowNumber) -> new RecentActivityRow(
                rs.getObject("transaction_id", UUID.class),
                rs.getString("activity_type"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getString("counterparty"),
                rs.getString("channel_detail"));
    }
}
