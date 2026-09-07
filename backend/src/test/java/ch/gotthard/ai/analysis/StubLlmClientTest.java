package ch.gotthard.ai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.ai.retrieval.RetrievedChunk;
import ch.gotthard.core.model.RiskLevel;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The offline adapter, on its own: no context, no database, no key.
 *
 * <p>Two properties are worth this much attention. It must be <em>deterministic</em> — a stub that
 * varied would make every integration test that asserts on a summary flaky, and would make the demo
 * unrehearsable. And it must be capable of <em>disagreeing</em> with the rules, because the column
 * that records disagreement cannot be tested by a stub that never does.
 */
class StubLlmClientTest {

    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-03-15T12:00:00Z");

    private final LlmClient stub = new StubLlmClient(JsonMapper.builder().build());

    @Test
    void given_theSameRequestTwice_when_analysed_then_theVerdictIsIdentical() {
        assertThat(stub.analyse(request(2)).verdict())
                .isEqualTo(stub.analyse(request(2)).verdict());
    }

    @Test
    void given_theStubAdapter_when_asked_then_itIdentifiesItselfAsTheStubAndNeverAsAModelProvider() {
        assertThat(stub.provider()).isEqualTo("stub");
        assertThat(stub.model()).isEqualTo("stub-analyst-v1");
    }

    @Test
    void given_noRuleFired_when_analysed_then_theAssessedLevelIsLow() {
        assertThat(stub.analyse(request(0)).verdict().assessedLevel()).isEqualTo(RiskLevel.LOW);
    }

    /** One pattern is a question, two a case, three or more a customer doing several things at once. */
    @Test
    void given_anIncreasingNumberOfDistinctRules_when_analysed_then_theAssessedLevelClimbsWithThem() {
        assertThat(assessedFor(1)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(assessedFor(2)).isEqualTo(RiskLevel.HIGH);
        assertThat(assessedFor(3)).isEqualTo(RiskLevel.CRITICAL);
        assertThat(assessedFor(5)).isEqualTo(RiskLevel.CRITICAL);
    }

    /**
     * The stub reads the count of distinct patterns; the rules read their weighted score. Feeding it
     * one heavily weighted rule is exactly the case where the two must part company.
     */
    @Test
    void given_oneHeavilyWeightedRule_when_analysed_then_theStubDisagreesWithTheComputedLevel() {
        final AnalysisRequest request = new AnalysisRequest(
                customer(),
                AT.minusDays(30),
                AT,
                new ComputedRisk(new BigDecimal("80.00"), RiskLevel.CRITICAL, 4),
                List.of(),
                firedRules(1),
                List.of());

        assertThat(stub.analyse(request).verdict().assessedLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void given_aFiredRule_when_analysed_then_theRecommendationsNameSomethingAHumanCanDo() {
        final List<String> recommendations = stub.analyse(request(1)).verdict().recommendations();

        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations.get(0))
                .contains("suspicious activity report")
                .contains("AML-001 §3");
        assertThat(recommendations).last().asString().contains("Record the outcome");
    }

    @Test
    void given_nothingFired_when_analysed_then_thereIsStillOneClosingAction() {
        assertThat(stub.analyse(request(0)).verdict().recommendations()).hasSize(1);
    }

    @Test
    void given_aFiredRule_when_analysed_then_theSummaryNamesTheCustomerTheRuleAndBothLevels() {
        final String summary = stub.analyse(request(1)).verdict().summary();

        assertThat(summary).contains("Sandra Wyss").contains("R-01").contains("MEDIUM");
    }

    @Test
    void given_anyRequest_when_analysed_then_theProvenanceNeededByTheAuditRowIsPopulated() {
        final LlmCompletion completion = stub.analyse(request(2));

        assertThat(completion.promptVersion()).isEqualTo(AnalysisPromptAssembler.VERSION);
        assertThat(completion.inputTokens()).isPositive();
        assertThat(completion.outputTokens()).isPositive();
        assertThat(completion.latencyMs()).isNotNegative();
        assertThat(completion.rawResponse()).contains("\"provider\":\"stub\"").contains("\"assessedLevel\"");
    }

    private RiskLevel assessedFor(final int firedRuleCount) {
        return stub.analyse(request(firedRuleCount)).verdict().assessedLevel();
    }

    private static AnalysisRequest request(final int firedRuleCount) {
        return new AnalysisRequest(
                customer(),
                AT.minusDays(30),
                AT,
                new ComputedRisk(new BigDecimal("30.00"), RiskLevel.MEDIUM, 4),
                List.of(new ChannelLine("PAYMENT", 4, "CHF", new BigDecimal("38600.00"), 0, AT.minusDays(6), AT)),
                firedRules(firedRuleCount),
                policy(firedRuleCount));
    }

    private static AnalysedCustomer customer() {
        return new AnalysedCustomer("CH-7002-4488", "Sandra Wyss", "CH", "RETAIL");
    }

    private static List<FiredRule> firedRules(final int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> new FiredRule(
                        "R-0" + index,
                        "Rule " + index,
                        "the condition of rule " + index,
                        index,
                        new BigDecimal("10.00"),
                        AT))
                .toList();
    }

    private static List<RetrievedChunk> policy(final int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> new RetrievedChunk(
                        UUID.nameUUIDFromBytes(("chunk-" + index).getBytes()),
                        "AML-00" + index,
                        "Detection criteria",
                        "3",
                        "policy body " + index,
                        0.9 - index * 0.01,
                        "R-0" + index))
                .toList();
    }
}
