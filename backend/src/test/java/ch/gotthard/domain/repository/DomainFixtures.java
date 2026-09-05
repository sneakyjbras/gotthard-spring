package ch.gotthard.domain.repository;

import ch.gotthard.domain.model.AiAnalysis;
import ch.gotthard.domain.model.AiAnalysisCitation;
import ch.gotthard.domain.model.CardActivity;
import ch.gotthard.domain.model.CryptoActivity;
import ch.gotthard.domain.model.Customer;
import ch.gotthard.domain.model.Operator;
import ch.gotthard.domain.model.OperatorCredentials;
import ch.gotthard.domain.model.OperatorRole;
import ch.gotthard.domain.model.PaymentActivity;
import ch.gotthard.domain.model.PolicyChunk;
import ch.gotthard.domain.model.RiskAssessment;
import ch.gotthard.domain.model.RiskLevel;
import ch.gotthard.domain.model.RiskRule;
import ch.gotthard.domain.model.RuleScope;
import ch.gotthard.domain.model.Transaction;
import ch.gotthard.domain.model.TransactionStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Minimal, valid entity instances for repository tests. One place to build fixtures instead of
 * repeating constructor calls — every field a table requires NOT NULL is filled with a sensible
 * value, and callers override only what their test actually cares about.
 */
final class DomainFixtures {

    private DomainFixtures() {}

    static Customer customer() {
        return customer(reference());
    }

    static Customer customer(String reference) {
        return new Customer(UUID.randomUUID(), reference, "Jane Doe", "CH", "RETAIL", OffsetDateTime.now());
    }

    static Operator operator() {
        return new Operator(
                UUID.randomUUID(),
                "operator-" + shortId(),
                "Jane Operator",
                OperatorRole.OPERATOR,
                OffsetDateTime.now());
    }

    static OperatorCredentials credentials(Operator operator) {
        return new OperatorCredentials(operator, "argon2id$dummy-hash", OffsetDateTime.now());
    }

    static CardActivity cardActivity(Customer customer, OffsetDateTime createdAt) {
        return new CardActivity(
                UUID.randomUUID(),
                customer,
                new BigDecimal("120.50"),
                "CHF",
                TransactionStatus.COMPLETED,
                createdAt,
                "4111111111111111",
                "DEBIT",
                "Coop Zurich",
                "5411",
                true,
                "AUTH123",
                null);
    }

    static PaymentActivity paymentActivity(Customer customer, OffsetDateTime createdAt) {
        return new PaymentActivity(
                UUID.randomUUID(),
                customer,
                new BigDecimal("5000.00"),
                "CHF",
                TransactionStatus.COMPLETED,
                createdAt,
                "SEPA",
                "CH9300762011623852957",
                "DE89370400440532013000",
                "DE");
    }

    static CryptoActivity cryptoActivity(Customer customer, OffsetDateTime createdAt) {
        return new CryptoActivity(
                UUID.randomUUID(),
                customer,
                new BigDecimal("0.05000000"),
                "BTC",
                TransactionStatus.COMPLETED,
                createdAt,
                "BITCOIN",
                "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
                "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy",
                "abc123hash",
                null);
    }

    static RiskRule riskRule() {
        return new RiskRule(
                UUID.randomUUID(),
                "R-" + shortId(),
                "High single-transaction amount",
                RuleScope.ALL,
                "amount > 10000",
                new BigDecimal("12.50"),
                true);
    }

    static RiskAssessment riskAssessment(Transaction transaction, RiskRule rule) {
        return new RiskAssessment(UUID.randomUUID(), transaction, rule, OffsetDateTime.now(), new BigDecimal("12.50"));
    }

    static PolicyChunk policyChunk() {
        return new PolicyChunk(
                UUID.randomUUID(),
                "AML-004",
                "Threshold reporting",
                "2.1",
                "Report cash transactions over CHF 15'000.",
                "{}");
    }

    static AiAnalysis aiAnalysis(Customer customer, Operator requestedBy, OffsetDateTime createdAt) {
        return aiAnalysis(customer, requestedBy, createdAt, RiskLevel.LOW, RiskLevel.LOW);
    }

    static AiAnalysis aiAnalysis(
            Customer customer,
            Operator requestedBy,
            OffsetDateTime createdAt,
            RiskLevel computedLevel,
            RiskLevel assessedLevel) {
        return new AiAnalysis(
                UUID.randomUUID(),
                customer,
                requestedBy,
                createdAt,
                createdAt.minusDays(30),
                createdAt,
                new BigDecimal("42.00"),
                computedLevel,
                assessedLevel,
                "Nothing unusual in the reviewed window.",
                "[]",
                "stub",
                "stub-v1",
                "v1",
                120,
                340,
                850,
                null);
    }

    static AiAnalysisCitation citation(AiAnalysis analysis, PolicyChunk chunk, float similarity, short rank) {
        return new AiAnalysisCitation(analysis, chunk, similarity, rank);
    }

    private static String reference() {
        return "CH-" + shortId();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
