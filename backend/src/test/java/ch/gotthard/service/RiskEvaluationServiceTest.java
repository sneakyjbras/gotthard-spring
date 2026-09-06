package ch.gotthard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.gotthard.core.model.RiskLevel;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * PostgreSQL computes, the core decides, the audit trail records — end to end, with the weights in a
 * table this test owns.
 *
 * <p>The structuring fixture is four payments to one beneficiary over six days: 9 500, 9 600, 9 700,
 * 9 800. Worked out by hand, the near-threshold count for each is 1, 2, 3, 4 and the seven-day volume
 * is 9 500, 19 100, 28 800, 38 600. R-01 wants three near-threshold amounts <em>and</em> ten thousand
 * in the week, so it fires on the third and fourth and on neither of the first two — which is the
 * assertion worth making: the rule is selective, not merely loud.
 */
class RiskEvaluationServiceTest extends AbstractServiceIntegrationTest {

    private static final OffsetDateTime ANCHOR = OffsetDateTime.parse("2026-03-15T12:00:00Z");

    @Autowired
    private RiskEvaluationService riskEvaluation;

    @BeforeEach
    void emptyTheRuleTable() {
        fixtures.clearRiskRules();
    }

    @Test
    void given_structuringAcrossAWeek_when_evaluate_then_theRuleFiresOnlyOnceThePatternIsComplete() {
        enableStructuringAndCorridorRules();
        final UUID customer = seedStructuring();

        final CustomerRiskReport report = riskEvaluation.evaluate(customer, window());

        assertThat(report.transactionsEvaluated()).isEqualTo(4);
        assertThat(report.findings()).hasSize(2);
        assertThat(report.findings()).extracting(RiskFinding::ruleCode).containsOnly("R-01");
        assertThat(report.findings())
                .extracting(RiskFinding::contribution)
                .containsOnly(new java.math.BigDecimal("30.00"));
    }

    /** The headline is the riskiest single activity, not a total that grows with the window. */
    @Test
    void given_twoTransactionsFiringOneRule_when_evaluate_then_theScoreIsTheHighestNotTheSum() {
        enableStructuringAndCorridorRules();

        final CustomerRiskReport report = riskEvaluation.evaluate(seedStructuring(), window());

        assertThat(report.score()).isEqualByComparingTo("30.00");
        assertThat(report.level()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void given_aRuleThatFires_when_evaluate_then_everyHitIsWrittenToTheAuditTrail() {
        enableStructuringAndCorridorRules();

        riskEvaluation.evaluate(seedStructuring(), window());

        assertThat(fixtures.countAssessments()).isEqualTo(2);
    }

    /** An operator refreshing the screen must not double the audit trail. */
    @Test
    void given_anEvaluationRunTwice_when_evaluate_then_theAuditTrailIsNotDuplicated() {
        enableStructuringAndCorridorRules();
        final UUID customer = seedStructuring();

        riskEvaluation.evaluate(customer, window());
        final CustomerRiskReport second = riskEvaluation.evaluate(customer, window());

        assertThat(fixtures.countAssessments()).isEqualTo(2);
        assertThat(second.findings()).hasSize(2);
    }

    /** No rules configured is a system that scores nought, not one that breaks. */
    @Test
    void given_anEmptyRiskRulesTable_when_evaluate_then_nothingScoresAndNothingIsRecorded() {
        final CustomerRiskReport report = riskEvaluation.evaluate(seedStructuring(), window());

        assertThat(report.score()).isEqualByComparingTo("0.00");
        assertThat(report.level()).isEqualTo(RiskLevel.LOW);
        assertThat(report.findings()).isEmpty();
        assertThat(fixtures.countAssessments()).isZero();
    }

    /** Switching a rule off is an {@code UPDATE}, and it stops scoring without a deployment. */
    @Test
    void given_theRuleDisabled_when_evaluate_then_itNeitherScoresNorRecords() {
        fixtures.riskRule("R-01", "Near-threshold structuring", "ALL", "30.00", false);

        final CustomerRiskReport report = riskEvaluation.evaluate(seedStructuring(), window());

        assertThat(report.findings()).isEmpty();
        assertThat(fixtures.countAssessments()).isZero();
    }

    /**
     * Three card-not-present declines in forty minutes across two merchants. By hand, only the last
     * of them sees all three within its hour, so R-03 fires once and the report says 25.
     */
    @Test
    void given_aCardTestingRun_when_evaluate_then_theCardRuleFiresOnTheTransactionThatCompletesIt() {
        fixtures.riskRule("R-03", "Card-not-present decline cluster", "CARD", "25.00", true);
        final UUID customer = fixtures.customer("CH");
        fixtures.card(customer, ANCHOR.minusMinutes(50), "10.00", "Beta", "5732", false, "DO_NOT_HONOR");
        fixtures.card(customer, ANCHOR.minusMinutes(30), "12.00", "Gamma", "5732", false, "DO_NOT_HONOR");
        final UUID third =
                fixtures.card(customer, ANCHOR.minusMinutes(10), "15.00", "Beta", "5732", false, "DO_NOT_HONOR");

        final CustomerRiskReport report = riskEvaluation.evaluate(customer, window());

        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.ruleCode()).isEqualTo("R-03");
            assertThat(finding.ruleName()).isEqualTo("Card-not-present decline cluster");
            assertThat(finding.transactionId()).isEqualTo(third);
        });
        assertThat(report.score()).isEqualByComparingTo("25.00");
    }

    /** A rule whose channel does not match the transaction never even gets asked. */
    @Test
    void given_onlyAPaymentRuleEnabled_when_evaluatingCardActivity_then_nothingFires() {
        fixtures.riskRule("R-02", "Elevated-risk corridor", "PAYMENT", "40.00", true);
        final UUID customer = fixtures.customer("CH");
        fixtures.card(customer, ANCHOR.minusMinutes(10), "15.00", "Beta", "5732", false, "DO_NOT_HONOR");

        assertThat(riskEvaluation.evaluate(customer, window()).findings()).isEmpty();
    }

    @Test
    void given_anUnknownCustomer_when_evaluate_then_itIsReportedAsMissing() {
        assertThatThrownBy(() -> riskEvaluation.evaluate(UUID.randomUUID(), window()))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void given_aCustomerWithNoActivity_when_evaluate_then_theReportIsCleanAndStatesItsWindow() {
        enableStructuringAndCorridorRules();

        final CustomerRiskReport report = riskEvaluation.evaluate(fixtures.customer("CH"), window());

        assertThat(report.transactionsEvaluated()).isZero();
        assertThat(report.score()).isEqualByComparingTo("0.00");
        assertThat(report.from()).isEqualTo(ANCHOR.minusDays(30));
        assertThat(report.to()).isEqualTo(ANCHOR);
    }

    /** R-02 is enabled throughout to prove the fixture fires R-01 on its merits, not by elimination. */
    private void enableStructuringAndCorridorRules() {
        fixtures.riskRule("R-01", "Near-threshold structuring", "ALL", "30.00", true);
        fixtures.riskRule("R-02", "Elevated-risk corridor", "PAYMENT", "40.00", true);
    }

    private UUID seedStructuring() {
        final UUID customer = fixtures.customer("CH");
        fixtures.payment(customer, ANCHOR.minusDays(6), "9500.00", "ACC-A", "CH");
        fixtures.payment(customer, ANCHOR.minusDays(4), "9600.00", "ACC-A", "CH");
        fixtures.payment(customer, ANCHOR.minusDays(2), "9700.00", "ACC-A", "CH");
        fixtures.payment(customer, ANCHOR, "9800.00", "ACC-A", "CH");
        return customer;
    }

    private static ActivityWindow window() {
        return ActivityWindow.between(Optional.of(ANCHOR.minusDays(30)), Optional.of(ANCHOR));
    }
}
