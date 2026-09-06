package ch.gotthard.domain.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Fixtures for the window-function tests, written as the {@code INSERT}s they are.
 *
 * <p>Plain SQL rather than JPA on purpose. These tests are about what PostgreSQL computes from a
 * particular arrangement of rows, so the arrangement should be visible and exact — no flush
 * ordering, no cascade, no entity graph between the test and the table. A test that has to reason
 * about Hibernate to know what is in the database cannot be trusted to prove what a window frame
 * contains.
 */
public final class QueryFixtures {

    private final NamedParameterJdbcTemplate jdbc;

    public QueryFixtures(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID customer(final String country) {
        final UUID customerId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO customers (customer_id, reference, full_name, country, segment, onboarded_at)
                VALUES (:customerId, :reference, 'Test Customer', :country, 'RETAIL', now())""",
                new MapSqlParameterSource()
                        .addValue("customerId", customerId)
                        .addValue(
                                "reference",
                                "REF-" + customerId.toString().substring(0, 8).toUpperCase())
                        .addValue("country", country));
        return customerId;
    }

    public UUID payment(
            final UUID customerId,
            final OffsetDateTime at,
            final String amount,
            final String receiverAccount,
            final String receiverBankCountry) {
        return payment(customerId, at, amount, "CHF", receiverAccount, receiverBankCountry);
    }

    public UUID payment(
            final UUID customerId,
            final OffsetDateTime at,
            final String amount,
            final String currency,
            final String receiverAccount,
            final String receiverBankCountry) {
        final UUID transactionId = transaction(customerId, at, amount, currency, "PAYMENT", "COMPLETED");
        jdbc.update(
                """
                INSERT INTO payment_activity
                    (transaction_id, payment_method, sender_account, receiver_account, receiver_bank_country)
                VALUES (:transactionId, 'SEPA', 'CH9300762011623852957', :receiverAccount, :receiverBankCountry)""",
                new MapSqlParameterSource()
                        .addValue("transactionId", transactionId)
                        .addValue("receiverAccount", receiverAccount)
                        .addValue("receiverBankCountry", receiverBankCountry));
        return transactionId;
    }

    /** A declined authorisation is one with a reason; the ledger status follows from that. */
    public UUID card(
            final UUID customerId,
            final OffsetDateTime at,
            final String amount,
            final String merchantName,
            final String mccCode,
            final boolean cardPresent,
            final String declineReason) {
        final UUID transactionId =
                transaction(customerId, at, amount, "CHF", "CARD", declineReason == null ? "COMPLETED" : "FAILED");
        jdbc.update(
                """
                INSERT INTO card_activity
                    (transaction_id, card_pan, card_type, merchant_name, mcc_code, card_present,
                     authorization_code, decline_reason)
                VALUES (:transactionId, '4111111111111111', 'DEBIT', :merchantName, :mccCode, :cardPresent,
                        :authorizationCode, :declineReason)""",
                new MapSqlParameterSource()
                        .addValue("transactionId", transactionId)
                        .addValue("merchantName", merchantName)
                        .addValue("mccCode", mccCode)
                        .addValue("cardPresent", cardPresent)
                        .addValue("authorizationCode", declineReason == null ? "AUTH123" : null)
                        .addValue("declineReason", declineReason));
        return transactionId;
    }

    public UUID crypto(
            final UUID customerId,
            final OffsetDateTime at,
            final String amount,
            final String walletFrom,
            final String walletTo,
            final String exchangeName) {
        return crypto(customerId, at, amount, "CHF", walletFrom, walletTo, exchangeName);
    }

    public UUID crypto(
            final UUID customerId,
            final OffsetDateTime at,
            final String amount,
            final String currency,
            final String walletFrom,
            final String walletTo,
            final String exchangeName) {
        final UUID transactionId = transaction(customerId, at, amount, currency, "CRYPTO", "COMPLETED");
        jdbc.update(
                """
                INSERT INTO crypto_activity
                    (transaction_id, blockchain, wallet_address_from, wallet_address_to, tx_hash, exchange_name)
                VALUES (:transactionId, 'BITCOIN', :walletFrom, :walletTo, :txHash, :exchangeName)""",
                new MapSqlParameterSource()
                        .addValue("transactionId", transactionId)
                        .addValue("walletFrom", walletFrom)
                        .addValue("walletTo", walletTo)
                        .addValue("txHash", "hash-" + transactionId)
                        .addValue("exchangeName", exchangeName));
        return transactionId;
    }

    /** A transaction whose status is worth setting explicitly — a reversal, or a failed transfer. */
    public UUID paymentWithStatus(
            final UUID customerId, final OffsetDateTime at, final String amount, final String status) {
        final UUID transactionId = transaction(customerId, at, amount, "CHF", "PAYMENT", status);
        jdbc.update(
                """
                INSERT INTO payment_activity
                    (transaction_id, payment_method, sender_account, receiver_account, receiver_bank_country)
                VALUES (:transactionId, 'SEPA', 'CH9300762011623852957', 'DE89370400440532013000', 'DE')""",
                new MapSqlParameterSource("transactionId", transactionId));
        return transactionId;
    }

    /**
     * Empties the rule table so a test's own weights are the only ones in play.
     *
     * <p>Rules are seeded by a migration, and a test that asserts a score has to know exactly which
     * ones were enabled. Inside a rolled-back transaction this affects nothing beyond the test.
     */
    public void clearRiskRules() {
        jdbc.getJdbcTemplate().update("DELETE FROM risk_assessments");
        jdbc.getJdbcTemplate().update("DELETE FROM risk_rules");
    }

    public UUID riskRule(
            final String ruleCode,
            final String ruleName,
            final String appliesTo,
            final String weight,
            final boolean enabled) {
        final UUID ruleId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO risk_rules
                    (rule_id, rule_code, rule_name, applies_to, threshold_logic, weight, enabled)
                VALUES (:ruleId, :ruleCode, :ruleName, :appliesTo, 'see the rule class', :weight, :enabled)""",
                new MapSqlParameterSource()
                        .addValue("ruleId", ruleId)
                        .addValue("ruleCode", ruleCode)
                        .addValue("ruleName", ruleName)
                        .addValue("appliesTo", appliesTo)
                        .addValue("weight", new BigDecimal(weight))
                        .addValue("enabled", enabled));
        return ruleId;
    }

    /** The generated reference of a seeded customer, for tests that search by the human identifier. */
    public String referenceOf(final UUID customerId) {
        return jdbc.queryForObject(
                "SELECT reference FROM customers WHERE customer_id = :customerId",
                new MapSqlParameterSource("customerId", customerId),
                String.class);
    }

    /** Rows on the audit trail, read straight off the table rather than through the use case. */
    public long countAssessments() {
        return jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM risk_assessments", Long.class);
    }

    private UUID transaction(
            final UUID customerId,
            final OffsetDateTime at,
            final String amount,
            final String currency,
            final String activityType,
            final String status) {
        final UUID transactionId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO transactions
                    (transaction_id, customer_id, activity_type, amount, currency, status, created_at)
                VALUES (:transactionId, :customerId, :activityType, :amount, :currency, :status, :createdAt)""",
                new MapSqlParameterSource()
                        .addValue("transactionId", transactionId)
                        .addValue("customerId", customerId)
                        .addValue("activityType", activityType)
                        .addValue("amount", new BigDecimal(amount))
                        .addValue("currency", currency)
                        .addValue("status", status)
                        .addValue("createdAt", at));
        return transactionId;
    }
}
