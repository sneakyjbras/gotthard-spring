package ch.gotthard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.gotthard.ai.analysis.AnalysisPromptAssembler;
import ch.gotthard.ai.analysis.LlmClient;
import ch.gotthard.ai.retrieval.EmbeddingModel;
import ch.gotthard.core.model.RiskLevel;
import ch.gotthard.domain.model.PolicyChunk;
import ch.gotthard.domain.repository.PolicyChunkRepository;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.test.context.TestPropertySource;

/**
 * The analysis use case end to end: real rules, real retrieval, real PostgreSQL, and the offline
 * adapter answering.
 *
 * <p><b>The key is pinned empty, and that is not decoration.</b> {@code LlmClientConfiguration}
 * chooses the adapter from {@code gotthard.ai.api-key}, which {@code application.properties} maps
 * from {@code ANTHROPIC_API_KEY}. Without this override, running the suite on a machine where that
 * variable happens to be exported would quietly send every test in this file to the real API — real
 * latency, real money, and a build that fails differently on every developer's laptop. Overriding
 * the property, rather than substituting a mock bean, means the wiring under test is the wiring that
 * ships: the same {@code StubLlmClient} a clean checkout runs with.
 */
@TestPropertySource(properties = "gotthard.ai.api-key=")
class AiAnalysisServiceTest extends AbstractServiceIntegrationTest {

    private static final OffsetDateTime ANCHOR = OffsetDateTime.parse("2026-03-15T12:00:00Z");
    private static final String OPERATOR = "e.rossi";

    @Autowired
    private AiAnalysisService analyses;

    @Autowired
    private LlmClient llm;

    @Autowired
    private PolicyChunkRepository policyChunks;

    @Autowired
    private EmbeddingModel embeddingModel;

    /**
     * {@code V3} seeds the corpus text and leaves the vectors {@code NULL}; the startup runner that
     * normally fills them in has no guarantee of having run before a test's own transaction. Doing it
     * here is idempotent — only unembedded chunks are touched — and the flush matters, because the
     * nearest-neighbour search is raw JDBC and will not see writes Hibernate is still holding.
     */
    @BeforeEach
    void embedTheCorpusAndOwnTheRules() {
        final List<PolicyChunk> unembedded = policyChunks.findByEmbeddingIsNull();
        unembedded.forEach(chunk -> chunk.setEmbedding(embeddingModel.embed(chunk.getBody())));
        policyChunks.saveAll(unembedded);
        policyChunks.flush();
        fixtures.clearRiskRules();
    }

    @Test
    void given_noApiKeyIsConfigured_when_theContextStarts_then_theOfflineAdapterIsTheOneWired() {
        assertThat(llm.provider()).isEqualTo("stub");
    }

    @Test
    void given_aCustomerTheRulesFlagged_when_analysed_then_everyColumnOfTheAuditRowIsWritten() {
        fixtures.riskRule("R-01", "Near-threshold structuring", "ALL", "30.00", true);

        final AiAnalysisView analysis = analyses.analyse(structuringCustomer(), window(), OPERATOR);

        assertThat(analysis.analysisId()).isNotNull();
        assertThat(analysis.createdAt()).isNotNull();
        assertThat(analysis.windowFrom()).isEqualTo(ANCHOR.minusDays(30));
        assertThat(analysis.windowTo()).isEqualTo(ANCHOR);
        assertThat(analysis.computedScore()).isEqualByComparingTo("30.00");
        assertThat(analysis.computedLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(analysis.assessedLevel()).isNotNull();
        assertThat(analysis.summary()).isNotBlank();
        assertThat(analysis.recommendations()).isNotEmpty();
        assertThat(analysis.provider()).isEqualTo("stub");
        assertThat(analysis.model()).isEqualTo("stub-analyst-v1");
        assertThat(analysis.promptVersion()).isEqualTo(AnalysisPromptAssembler.VERSION);
        assertThat(analysis.inputTokens()).isPositive();
        assertThat(analysis.outputTokens()).isPositive();
        assertThat(analysis.latencyMs()).isNotNegative();
    }

    @Test
    void given_anAnalysisWasRun_when_theRowIsReadBack_then_theOperatorAndTheRawResponseAreOnIt() {
        fixtures.riskRule("R-01", "Near-threshold structuring", "ALL", "30.00", true);

        final AiAnalysisView analysis = analyses.analyse(structuringCustomer(), window(), OPERATOR);

        assertThat(analysis.requestedBy().username()).isEqualTo(OPERATOR);
        assertThat(analysis.requestedBy().displayName()).isEqualTo("Elena Rossi");
        // Read back as jsonb, so PostgreSQL has already normalised key order and spacing — assert on
        // what is in the document, not on the byte-for-byte text the adapter happened to write.
        final Map<String, Object> row = storedRow(analysis.analysisId());
        assertThat(row.get("raw_response"))
                .asString()
                .contains("\"provider\"")
                .contains("\"stub\"")
                .contains("\"assessedLevel\"");
        assertThat(row.get("recommendations")).asString().startsWith("[");
        assertThat(row.get("requested_by")).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void given_policyWasRetrieved_when_theAnalysisIsWritten_then_everyChunkShownIsCitedAgainstARealChunk() {
        fixtures.riskRule("R-01", "Near-threshold structuring", "ALL", "30.00", true);

        final AiAnalysisView analysis = analyses.analyse(structuringCustomer(), window(), OPERATOR);

        assertThat(analysis.citations()).isNotEmpty();
        assertThat(analysis.citations()).allSatisfy(citation -> {
            assertThat(citation.document()).isNotBlank();
            assertThat(citation.body()).isNotBlank();
            assertThat(citation.similarity()).isGreaterThan(0.0);
        });
        assertThat(analysis.citations()).extracting(PolicyCitationView::rank).isEqualTo(ranksUpTo(analysis));
        assertThat(citationsJoinedToRealChunks(analysis.analysisId()))
                .isEqualTo(analysis.citations().size());
    }

    /**
     * One rule weighted at 80 scores CRITICAL; the offline adapter reads a single distinct pattern
     * and says MEDIUM. The point is not which is right — it is that the disagreement is on the row,
     * computed by PostgreSQL, rather than resolved away in Java.
     */
    @Test
    void given_theModelReadsTheEvidenceDifferently_when_analysed_then_theDivergenceIsRecorded() {
        fixtures.riskRule("R-01", "Near-threshold structuring", "ALL", "80.00", true);

        final AiAnalysisView analysis = analyses.analyse(structuringCustomer(), window(), OPERATOR);

        assertThat(analysis.computedLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(analysis.assessedLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(analysis.levelsDiverged()).isTrue();
        assertThat(storedRow(analysis.analysisId()).get("levels_diverged")).isEqualTo(true);
    }

    @Test
    void given_theTwoReadingsAgree_when_analysed_then_noDivergenceIsRecorded() {
        fixtures.riskRule("R-01", "Near-threshold structuring", "ALL", "30.00", true);

        final AiAnalysisView analysis = analyses.analyse(structuringCustomer(), window(), OPERATOR);

        assertThat(analysis.computedLevel()).isEqualTo(analysis.assessedLevel());
        assertThat(analysis.levelsDiverged()).isFalse();
        assertThat(storedRow(analysis.analysisId()).get("levels_diverged")).isEqualTo(false);
    }

    @Test
    void given_severalAnalysesOfOneCustomer_when_theHistoryIsRead_then_theNewestComesFirst() {
        fixtures.riskRule("R-01", "Near-threshold structuring", "ALL", "30.00", true);
        final UUID customer = structuringCustomer();
        final UUID first = analyses.analyse(customer, window(), OPERATOR).analysisId();
        final UUID second = analyses.analyse(customer, window(), OPERATOR).analysisId();
        final UUID third = analyses.analyse(customer, window(), OPERATOR).analysisId();

        final List<AiAnalysisView> history = analyses.history(customer);

        assertThat(history).extracting(AiAnalysisView::analysisId).containsExactly(third, second, first);
        assertThat(history).extracting(AiAnalysisView::createdAt).isSortedAccordingTo(Comparator.reverseOrder());
    }

    /** History answers "who asked what, when"; a page of policy text per entry answers nobody's question. */
    @Test
    void given_ananalysisWithCitations_when_theHistoryIsRead_then_thePolicyTextIsLeftOut() {
        fixtures.riskRule("R-01", "Near-threshold structuring", "ALL", "30.00", true);
        final UUID customer = structuringCustomer();
        analyses.analyse(customer, window(), OPERATOR);

        assertThat(analyses.history(customer)).singleElement().satisfies(entry -> {
            assertThat(entry.citations()).isEmpty();
            assertThat(entry.summary()).isNotBlank();
        });
    }

    @Test
    void given_anAnalysisId_when_readOnItsOwn_then_theCitedPolicyComesBackWithIt() {
        fixtures.riskRule("R-01", "Near-threshold structuring", "ALL", "30.00", true);
        final UUID analysisId =
                analyses.analyse(structuringCustomer(), window(), OPERATOR).analysisId();

        final AiAnalysisView reread = analyses.find(analysisId);

        assertThat(reread.analysisId()).isEqualTo(analysisId);
        assertThat(reread.citations()).isNotEmpty();
        assertThat(reread.citations()).extracting(PolicyCitationView::rank).startsWith(1);
    }

    /** Nothing fired, so nothing was retrieved — and an analysis with no citations is still an analysis. */
    @Test
    void given_aCustomerNothingFiredOn_when_analysed_then_itIsRecordedWithNoCitationsAtAll() {
        final UUID customer = fixtures.customer("CH");
        fixtures.card(customer, ANCHOR.minusDays(2), "43.07", "Coop", "5411", true, null);

        final AiAnalysisView analysis = analyses.analyse(customer, window(), OPERATOR);

        assertThat(analysis.computedLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(analysis.assessedLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(analysis.levelsDiverged()).isFalse();
        assertThat(analysis.citations()).isEmpty();
        assertThat(analysis.summary()).contains("no rule-level concern");
    }

    @Test
    void given_anUnknownCustomer_when_analysed_then_itIsReportedAsMissing() {
        assertThatThrownBy(() -> analyses.analyse(UUID.randomUUID(), window(), OPERATOR))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void given_anUnknownAnalysisId_when_read_then_itIsReportedAsMissing() {
        assertThatThrownBy(() -> analyses.find(UUID.randomUUID())).isInstanceOf(AiAnalysisNotFoundException.class);
    }

    private static List<Integer> ranksUpTo(final AiAnalysisView analysis) {
        return java.util.stream.IntStream.rangeClosed(1, analysis.citations().size())
                .boxed()
                .toList();
    }

    private Map<String, Object> storedRow(final UUID analysisId) {
        return jdbc.queryForMap(
                "SELECT * FROM ai_analyses WHERE analysis_id = :analysisId",
                new MapSqlParameterSource("analysisId", analysisId));
    }

    /** Proves the citations point at rows that exist, rather than at ids that merely look plausible. */
    private int citationsJoinedToRealChunks(final UUID analysisId) {
        return jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM ai_analysis_citations c
                JOIN policy_chunks p ON p.chunk_id = c.chunk_id
                WHERE c.analysis_id = :analysisId""",
                new MapSqlParameterSource("analysisId", analysisId),
                Integer.class);
    }

    private UUID structuringCustomer() {
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
