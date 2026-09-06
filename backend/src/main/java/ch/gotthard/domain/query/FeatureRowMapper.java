package ch.gotthard.domain.query;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/**
 * Columns to {@link FeatureRow}, one small routine per channel.
 *
 * <p>Nothing is interpreted here beyond what JDBC requires: a SQL NULL that means "never happened"
 * stays null, so the assembler above can give it the value the risk core reserves for that.
 */
final class FeatureRowMapper implements RowMapper<FeatureRow> {

    @Override
    public FeatureRow mapRow(final ResultSet rs, final int rowNumber) throws SQLException {
        return new FeatureRow(activity(rs), velocity(rs), card(rs), payment(rs), crypto(rs));
    }

    private static ActivityRow activity(final ResultSet rs) throws SQLException {
        return new ActivityRow(
                rs.getObject("transaction_id", UUID.class),
                rs.getObject("customer_id", UUID.class),
                rs.getString("activity_type"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getBigDecimal("reporting_amount"),
                rs.getString("reporting_currency"));
    }

    private static VelocityRow velocity(final ResultSet rs) throws SQLException {
        return new VelocityRow(
                rs.getBigDecimal("rolling_volume_24h"),
                rs.getBigDecimal("rolling_volume_7d"),
                rs.getLong("transaction_count_24h"),
                rs.getLong("near_threshold_count_7d"),
                rs.getLong("dormancy_seconds"));
    }

    private static CardRow card(final ResultSet rs) throws SQLException {
        return new CardRow(
                rs.getLong("decline_count_1h"),
                rs.getLong("card_not_present_decline_count_1h"),
                rs.getLong("distinct_merchant_count_1h"),
                rs.getBigDecimal("quasi_cash_volume_24h"));
    }

    private static PaymentRow payment(final ResultSet rs) throws SQLException {
        return new PaymentRow(
                countries(rs.getArray("counterparty_countries_7d")),
                rs.getLong("cross_border_count_7d"),
                rs.getBigDecimal("cross_border_volume_7d"));
    }

    private static CryptoRow crypto(final ResultSet rs) throws SQLException {
        return new CryptoRow(
                rs.getBoolean("destination_is_exchange"),
                nullableLong(rs, "since_inbound_funding_seconds"),
                rs.getBigDecimal("crypto_outbound_volume_24h"),
                rs.getString("sending_wallet"));
    }

    /** Null on any row that was not a payment; the aggregate itself never produces an empty array. */
    private static List<String> countries(final Array countries) throws SQLException {
        return countries == null ? List.of() : Arrays.asList((String[]) countries.getArray());
    }

    /** Null means the thing never happened, which is different from having happened nought ago. */
    private static Long nullableLong(final ResultSet rs, final String column) throws SQLException {
        final long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
