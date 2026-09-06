package ch.gotthard.domain.query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The window functions behind every risk score: one pass over a customer's timeline that leaves each
 * transaction carrying the aggregates the rules will read.
 *
 * <h2>RANGE, not ROWS</h2>
 *
 * <p>Every rolling frame here is {@code RANGE BETWEEN INTERVAL '…' PRECEDING AND CURRENT ROW}. RANGE
 * is a frame over <em>values</em> of the {@code ORDER BY} column: it admits exactly those rows whose
 * {@code created_at} lies in {@code [current − interval, current]}. ROWS is a frame over
 * <em>positions</em> — "the previous n rows" — and the two agree only when transactions happen to
 * arrive at a constant rate, which they never do. A day with three transactions and a day with
 * three hundred would both be summed as "the last three" under ROWS, and nothing would fail; the
 * numbers would simply be wrong. That is the whole reason this file is a window query and not a
 * loop.
 *
 * <p>Two consequences of RANGE are relied on deliberately:
 *
 * <ul>
 *   <li><b>The bound is inclusive.</b> A transaction exactly twenty-four hours old is inside the
 *       twenty-four hour frame.
 *   <li><b>Peers are all in the frame.</b> Rows sharing a {@code created_at} are peers, and every
 *       peer sees every other one — including those the sort happened to place after it. Under ROWS
 *       the answer would depend on an arbitrary tie-break, which is a non-deterministic risk score.
 * </ul>
 *
 * <h2>What is not a window function</h2>
 *
 * <p>Two signals cannot be: PostgreSQL has no {@code COUNT(DISTINCT …) OVER (…)}, so the distinct
 * merchant count is a correlated subquery whose bounds are written to match the RANGE frame exactly;
 * and hops to a flagged wallet is a graph walk, which arrives from elsewhere entirely.
 *
 * <h2>History versus window</h2>
 *
 * <p>The frames are computed over the customer's whole timeline up to {@code to}, and only then are
 * the rows outside {@code [from, to]} discarded. Filtering first would leave the earliest reported
 * transaction with a seven-day sum containing one row.
 */
@Repository
public class FeatureWindowQuery {

    /**
     * Merchant categories where a card comes closest to being a cash machine: manual cash
     * disbursement, ATM, financial institutions, quasi-cash and crypto, money transfer, and
     * gambling. Classifying an MCC is a lookup, and lookups belong in the query rather than in a
     * rule.
     */
    public static final List<String> QUASI_CASH_MCC_CODES =
            List.of("6010", "6011", "6012", "6051", "4829", "7995", "7800", "7801", "7802");

    private static final String SQL = String.join(
                    ",\n",
                    "WITH " + SqlFragments.RATES_CTE,
                    timelineCte(),
                    velocityCte(),
                    nearThresholdCte(),
                    cardTimelineCte(),
                    cardWindowsCte(),
                    paymentTimelineCte(),
                    paymentWindowsCte(),
                    customerWalletsCte(),
                    walletEventsCte(),
                    walletWindowsCte(),
                    cryptoCte())
            + "\n" + selectClause();

    private final NamedParameterJdbcTemplate jdbc;
    private final FeatureRowMapper mapper = new FeatureRowMapper();

    public FeatureWindowQuery(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Every transaction the customer made in {@code [from, to]}, each carrying the aggregates of the
     * history behind it, oldest first.
     */
    public List<FeatureRow> findFeatures(
            final UUID customerId,
            final OffsetDateTime from,
            final OffsetDateTime to,
            final CurrencyConversion conversion,
            final NearThresholdBand band) {
        return jdbc.query(SQL, parameters(customerId, from, to, conversion, band), mapper);
    }

    private static MapSqlParameterSource parameters(
            final UUID customerId,
            final OffsetDateTime from,
            final OffsetDateTime to,
            final CurrencyConversion conversion,
            final NearThresholdBand band) {
        return new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("from", from)
                .addValue("to", to)
                .addValue("rateCurrencies", conversion.currencyCodes())
                .addValue("rateValues", conversion.rates())
                .addValue("reportingCurrency", conversion.reportingCurrency())
                .addValue("nearThresholdFloor", band.floor())
                .addValue("reportingThreshold", band.threshold())
                .addValue("quasiCashMccCodes", QUASI_CASH_MCC_CODES);
    }

    /**
     * The customer's timeline in reporting currency, with each channel's detail row flattened
     * alongside. {@code counterparty} is whoever was on the other side, whichever channel that came
     * through — the beneficiary account, the receiving wallet, or the merchant.
     */
    private static String timelineCte() {
        return """
                timeline AS (
                    SELECT t.transaction_id,
                           t.customer_id,
                           t.activity_type,
                           t.created_at,
                           t.status,
                           cu.country AS customer_country,
                           %s AS reporting_amount,
                           COALESCE(pa.receiver_account, cr.wallet_address_to, ca.merchant_name) AS counterparty,
                           ca.merchant_name,
                           ca.mcc_code,
                           ca.card_present,
                           ca.decline_reason,
                           pa.receiver_bank_country,
                           cr.wallet_address_from,
                           cr.exchange_name
                    FROM transactions t
                    JOIN customers cu ON cu.customer_id = t.customer_id
                    LEFT JOIN rates r ON r.currency = t.currency
                    LEFT JOIN card_activity ca ON ca.transaction_id = t.transaction_id
                    LEFT JOIN payment_activity pa ON pa.transaction_id = t.transaction_id
                    LEFT JOIN crypto_activity cr ON cr.transaction_id = t.transaction_id
                    WHERE t.customer_id = :customerId
                      AND t.created_at <= :to
                )"""
                .formatted(SqlFragments.REPORTING_AMOUNT);
    }

    /**
     * Rolling sums and the count, plus the dormancy gap.
     *
     * <p>{@code LAG} is the one place ordering rather than a frame is wanted: the gap is to the
     * previous activity, however long ago that was, and the first transaction of a customer's life
     * has no predecessor — nought, so a first transaction never reads as a return from dormancy.
     */
    private static String velocityCte() {
        return """
                velocity AS (
                    SELECT transaction_id,
                           SUM(reporting_amount) OVER rolling_24h AS rolling_volume_24h,
                           SUM(reporting_amount) OVER rolling_7d  AS rolling_volume_7d,
                           COUNT(*)              OVER rolling_24h AS transaction_count_24h,
                           COALESCE(CAST(EXTRACT(EPOCH FROM (
                               created_at - LAG(created_at) OVER (PARTITION BY customer_id ORDER BY created_at)
                           )) AS BIGINT), 0) AS dormancy_seconds
                    FROM timeline
                    WINDOW rolling_24h AS (PARTITION BY customer_id ORDER BY created_at
                                           RANGE BETWEEN INTERVAL '24 hours' PRECEDING AND CURRENT ROW),
                           rolling_7d  AS (PARTITION BY customer_id ORDER BY created_at
                                           RANGE BETWEEN INTERVAL '7 days' PRECEDING AND CURRENT ROW)
                )""";
    }

    /**
     * Near-threshold amounts to one beneficiary over seven days.
     *
     * <p>Partitioned by counterparty as well as customer, because that is the pattern: the same
     * destination, repeatedly, each payment sized to stay under the line. Spread across unrelated
     * beneficiaries the same amounts are just a week of ordinary business.
     */
    private static String nearThresholdCte() {
        return """
                near_threshold AS (
                    SELECT transaction_id,
                           COUNT(*) FILTER (
                               WHERE reporting_amount >= CAST(:nearThresholdFloor AS numeric)
                                 AND reporting_amount <  CAST(:reportingThreshold AS numeric)
                           ) OVER (PARTITION BY customer_id, counterparty ORDER BY created_at
                                   RANGE BETWEEN INTERVAL '7 days' PRECEDING AND CURRENT ROW)
                           AS near_threshold_count_7d
                    FROM timeline
                )""";
    }

    /**
     * A declined authorisation is one the issuer gave a reason for, or one the ledger marked failed
     * — the two ways this schema records the same refusal.
     */
    private static String cardTimelineCte() {
        return """
                card_timeline AS (
                    SELECT transaction_id,
                           customer_id,
                           created_at,
                           reporting_amount,
                           mcc_code,
                           card_present,
                           (decline_reason IS NOT NULL OR status = 'FAILED') AS declined
                    FROM timeline
                    WHERE activity_type = 'CARD'
                )""";
    }

    private static String cardWindowsCte() {
        return """
                card_windows AS (
                    SELECT transaction_id,
                           COUNT(*) FILTER (WHERE declined) OVER card_hour AS decline_count_1h,
                           COUNT(*) FILTER (WHERE declined AND NOT card_present) OVER card_hour
                               AS card_not_present_decline_count_1h,
                           COALESCE(SUM(reporting_amount) FILTER (
                               WHERE CAST(mcc_code AS text) IN (:quasiCashMccCodes)
                           ) OVER card_day, 0) AS quasi_cash_volume_24h
                    FROM card_timeline
                    WINDOW card_hour AS (PARTITION BY customer_id ORDER BY created_at
                                         RANGE BETWEEN INTERVAL '1 hour' PRECEDING AND CURRENT ROW),
                           card_day  AS (PARTITION BY customer_id ORDER BY created_at
                                         RANGE BETWEEN INTERVAL '24 hours' PRECEDING AND CURRENT ROW)
                )""";
    }

    private static String paymentTimelineCte() {
        return """
                payment_timeline AS (
                    SELECT transaction_id,
                           customer_id,
                           created_at,
                           reporting_amount,
                           receiver_bank_country,
                           customer_country
                    FROM timeline
                    WHERE activity_type = 'PAYMENT'
                )""";
    }

    /**
     * Corridors over seven days. Cross-border is measured against the customer's own country, not
     * against a list — which countries are elevated risk is a policy question the rule asks, using
     * the countries this window collected.
     */
    private static String paymentWindowsCte() {
        return """
                payment_windows AS (
                    SELECT transaction_id,
                           array_agg(receiver_bank_country) OVER payment_week AS counterparty_countries_7d,
                           COUNT(*) FILTER (WHERE receiver_bank_country <> customer_country) OVER payment_week
                               AS cross_border_count_7d,
                           COALESCE(SUM(reporting_amount) FILTER (
                               WHERE receiver_bank_country <> customer_country
                           ) OVER payment_week, 0) AS cross_border_volume_7d
                    FROM payment_timeline
                    WINDOW payment_week AS (PARTITION BY customer_id ORDER BY created_at
                                            RANGE BETWEEN INTERVAL '7 days' PRECEDING AND CURRENT ROW)
                )""";
    }

    /** The wallets this customer spent from in the reported window — the only ones worth walking. */
    private static String customerWalletsCte() {
        return """
                customer_wallets AS (
                    SELECT DISTINCT wallet_address_from AS wallet
                    FROM timeline
                    WHERE activity_type = 'CRYPTO'
                      AND created_at >= :from
                )""";
    }

    /**
     * A wallet's ledger: one row per transfer per end of it. Not restricted to this customer on
     * purpose — money arriving at a wallet is money arriving at it whoever sent it, and a
     * pass-through is only visible if the inbound leg is in view.
     */
    private static String walletEventsCte() {
        return """
                wallet_events AS (
                    SELECT cr.wallet_address_from AS wallet,
                           t.transaction_id,
                           t.created_at,
                           TRUE AS outbound,
                           %1$s AS reporting_amount
                    FROM crypto_activity cr
                    JOIN transactions t ON t.transaction_id = cr.transaction_id
                    JOIN customer_wallets w ON w.wallet = cr.wallet_address_from
                    LEFT JOIN rates r ON r.currency = t.currency
                    WHERE t.created_at <= :to
                    UNION ALL
                    SELECT cr.wallet_address_to,
                           t.transaction_id,
                           t.created_at,
                           FALSE,
                           %1$s
                    FROM crypto_activity cr
                    JOIN transactions t ON t.transaction_id = cr.transaction_id
                    JOIN customer_wallets w ON w.wallet = cr.wallet_address_to
                    LEFT JOIN rates r ON r.currency = t.currency
                    WHERE t.created_at <= :to
                )"""
                .formatted(SqlFragments.REPORTING_AMOUNT);
    }

    /**
     * The day's outflow, and when this wallet was last funded.
     *
     * <p>{@code RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW} rather than ROWS for the funding
     * lookup: unbounded because funding may have arrived at any point in the wallet's life, and
     * RANGE because an inbound transfer sharing this transfer's timestamp is a peer that belongs in
     * the frame no matter which of the two the sort put first.
     */
    private static String walletWindowsCte() {
        return """
                wallet_windows AS (
                    SELECT transaction_id,
                           outbound,
                           created_at,
                           COALESCE(SUM(reporting_amount) FILTER (WHERE outbound) OVER wallet_day, 0)
                               AS outbound_volume_24h,
                           MAX(created_at) FILTER (WHERE NOT outbound) OVER wallet_history AS last_inbound_at
                    FROM wallet_events
                    WINDOW wallet_day AS (PARTITION BY wallet ORDER BY created_at
                                          RANGE BETWEEN INTERVAL '24 hours' PRECEDING AND CURRENT ROW),
                           wallet_history AS (PARTITION BY wallet ORDER BY created_at
                                              RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
                )""";
    }

    /** Only the outbound leg is a transaction of this customer's; the inbound legs were context. */
    private static String cryptoCte() {
        return """
                crypto AS (
                    SELECT transaction_id,
                           outbound_volume_24h,
                           CAST(EXTRACT(EPOCH FROM (created_at - last_inbound_at)) AS BIGINT)
                               AS since_inbound_funding_seconds
                    FROM wallet_windows
                    WHERE outbound
                )""";
    }

    /**
     * The distinct merchant count's bounds are written to match a {@code RANGE … '1 hour' PRECEDING}
     * frame exactly — inclusive at both ends, so peers are counted — because PostgreSQL will not
     * accept {@code COUNT(DISTINCT …) OVER (…)} and an approximation here would be a silently
     * different number from the decline counts beside it.
     */
    private static String selectClause() {
        return """
                SELECT tl.transaction_id,
                       tl.customer_id,
                       tl.activity_type,
                       tl.created_at,
                       tl.reporting_amount,
                       CAST(:reportingCurrency AS text) AS reporting_currency,
                       v.rolling_volume_24h,
                       v.rolling_volume_7d,
                       v.transaction_count_24h,
                       v.dormancy_seconds,
                       nt.near_threshold_count_7d,
                       COALESCE(cw.decline_count_1h, 0) AS decline_count_1h,
                       COALESCE(cw.card_not_present_decline_count_1h, 0) AS card_not_present_decline_count_1h,
                       COALESCE(cw.quasi_cash_volume_24h, 0) AS quasi_cash_volume_24h,
                       CASE WHEN tl.activity_type = 'CARD' THEN (
                           SELECT COUNT(DISTINCT ca2.merchant_name)
                           FROM transactions t2
                           JOIN card_activity ca2 ON ca2.transaction_id = t2.transaction_id
                           WHERE t2.customer_id = tl.customer_id
                             AND t2.created_at >= tl.created_at - INTERVAL '1 hour'
                             AND t2.created_at <= tl.created_at
                       ) ELSE 0 END AS distinct_merchant_count_1h,
                       pw.counterparty_countries_7d,
                       COALESCE(pw.cross_border_count_7d, 0) AS cross_border_count_7d,
                       COALESCE(pw.cross_border_volume_7d, 0) AS cross_border_volume_7d,
                       (tl.exchange_name IS NOT NULL) AS destination_is_exchange,
                       cy.since_inbound_funding_seconds,
                       COALESCE(cy.outbound_volume_24h, 0) AS crypto_outbound_volume_24h,
                       tl.wallet_address_from AS sending_wallet
                FROM timeline tl
                JOIN velocity v ON v.transaction_id = tl.transaction_id
                JOIN near_threshold nt ON nt.transaction_id = tl.transaction_id
                LEFT JOIN card_windows cw ON cw.transaction_id = tl.transaction_id
                LEFT JOIN payment_windows pw ON pw.transaction_id = tl.transaction_id
                LEFT JOIN crypto cy ON cy.transaction_id = tl.transaction_id
                WHERE tl.created_at >= :from
                ORDER BY tl.created_at, tl.transaction_id""";
    }
}
