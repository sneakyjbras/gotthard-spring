package ch.gotthard.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.core.model.RiskLevel;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The whole pipeline on the demo dataset, with the migration's own weights.
 *
 * <p>Nothing here is arranged: the rules come from {@code V4__seed_demo_data.sql}, the transactions
 * come from it too, and the arithmetic is the seed author's tuning meeting the core's bands. R-01 is
 * weighted 44 and R-02 is weighted 32, and both fire on Sandra Wyss's later transfers — 44 + 32 = 76,
 * which clears CRITICAL's floor of 75 by four points. That compounding is exactly what the seed's
 * comment says it was tuned for, and this test is where the claim is checked rather than asserted.
 *
 * <p>Worked out by hand from the timestamps (see {@code SeededNarrativeWindowTest} for the frames):
 * R-01 needs three near-threshold transfers to one beneficiary and ten thousand in the week, so it
 * fires on the third, fourth and fifth. R-02 needs an elevated-risk corridor plus either a second
 * cross-border payment in the week or five thousand of them, and the first transfer already carries
 * 9 450 — so it fires on all five. Three plus five is eight findings, and eight audit rows.
 */
class SeededNarrativeRiskTest extends AbstractServiceIntegrationTest {

    private static final UUID SANDRA_WYSS = UUID.fromString("14781492-5cc9-5660-a44e-8330e87e7736");
    private static final UUID LIVIA_BAUMANN = UUID.fromString("a61bc67a-fc1a-576b-9eb2-6d5463806f13");

    @Autowired
    private RiskEvaluationService riskEvaluation;

    @Test
    void given_theSeededStructuringNarrative_when_evaluate_then_twoRulesCompoundIntoCritical() {
        final CustomerRiskReport report = riskEvaluation.evaluate(SANDRA_WYSS, lastThirtyDays());

        assertThat(report.score()).isEqualByComparingTo("76.00");
        assertThat(report.level()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(report.customer().reference()).isEqualTo("CH-7002-4488");
    }

    @Test
    void given_theSeededStructuringNarrative_when_evaluate_then_bothTypologiesAreNamedInTheFindings() {
        final CustomerRiskReport report = riskEvaluation.evaluate(SANDRA_WYSS, lastThirtyDays());

        assertThat(report.findings()).hasSize(8);
        assertThat(report.findings())
                .filteredOn(finding -> finding.ruleCode().equals("R-01"))
                .hasSize(3)
                .allSatisfy(finding -> assertThat(finding.contribution()).isEqualByComparingTo("44.00"));
        assertThat(report.findings())
                .filteredOn(finding -> finding.ruleCode().equals("R-02"))
                .hasSize(5)
                .allSatisfy(finding -> assertThat(finding.contribution()).isEqualByComparingTo("32.00"));
    }

    @Test
    void given_theSeededStructuringNarrative_when_evaluate_then_everyFindingReachesTheAuditTrail() {
        riskEvaluation.evaluate(SANDRA_WYSS, lastThirtyDays());

        assertThat(fixtures.countAssessments()).isEqualTo(8);
    }

    /**
     * The clean customer has to come out clean. A rule set that flags the structuring narrative and
     * also flags an ordinary retail customer has not detected anything.
     */
    @Test
    void given_theSeededCleanCustomer_when_evaluate_then_noRuleFires() {
        final CustomerRiskReport report = riskEvaluation.evaluate(LIVIA_BAUMANN, lastThirtyDays());

        assertThat(report.customer().reference()).isEqualTo("CH-7002-4471");
        assertThat(report.transactionsEvaluated()).isPositive();
        assertThat(report.findings()).isEmpty();
        assertThat(report.score()).isEqualByComparingTo("0.00");
        assertThat(report.level()).isEqualTo(RiskLevel.LOW);
        assertThat(fixtures.countAssessments()).isZero();
    }

    private static ActivityWindow lastThirtyDays() {
        return ActivityWindow.between(Optional.of(OffsetDateTime.now().minusDays(30)), Optional.empty());
    }
}
