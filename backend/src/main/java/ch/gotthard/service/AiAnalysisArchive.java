package ch.gotthard.service;

import ch.gotthard.ai.analysis.AnalysisVerdict;
import ch.gotthard.ai.analysis.LlmCompletion;
import ch.gotthard.ai.retrieval.RetrievedChunk;
import ch.gotthard.core.model.RiskLevel;
import ch.gotthard.domain.model.AiAnalysis;
import ch.gotthard.domain.model.AiAnalysisCitation;
import ch.gotthard.domain.model.Customer;
import ch.gotthard.domain.model.Operator;
import ch.gotthard.domain.model.PolicyChunk;
import ch.gotthard.domain.repository.AiAnalysisCitationRepository;
import ch.gotthard.domain.repository.AiAnalysisRepository;
import ch.gotthard.domain.repository.CustomerRepository;
import ch.gotthard.domain.repository.OperatorRepository;
import ch.gotthard.domain.repository.PolicyChunkRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code ai_analyses} and {@code ai_analysis_citations}, written and read back as views.
 *
 * <p>Both halves live together because they are the same decision seen twice: what an analysis
 * record contains, and how it is read. Splitting them would leave two places to change when a column
 * is added, and one of them would be forgotten.
 *
 * <p><b>{@code levels_diverged} is never written.</b> It is {@code GENERATED ALWAYS ... STORED} —
 * PostgreSQL compares the two levels, and any attempt to say otherwise from Java is rejected by the
 * database. What this class does instead is {@link EntityManager#refresh} the row after the insert,
 * so the value reported to the operator is the one the database computed rather than one recomputed
 * here from the same two fields and hoped to agree.
 *
 * <p><b>Every retrieved chunk is cited, in retrieval order.</b> Rank is the position the retriever
 * put it in, similarity the score that earned it that position — the citation list is a record of
 * what the model was shown, not of what it happened to quote.
 */
@Component
class AiAnalysisArchive {

    private static final TypeReference<List<String>> RECOMMENDATIONS = new TypeReference<>() {};

    private final AiAnalysisRepository analyses;
    private final AiAnalysisCitationRepository citations;
    private final PolicyChunkRepository policyChunks;
    private final CustomerRepository customers;
    private final OperatorRepository operators;
    private final EntityManager entityManager;
    private final ObjectMapper json;

    AiAnalysisArchive(
            final AiAnalysisRepository analyses,
            final AiAnalysisCitationRepository citations,
            final PolicyChunkRepository policyChunks,
            final CustomerRepository customers,
            final OperatorRepository operators,
            final EntityManager entityManager,
            final ObjectMapper json) {
        this.analyses = analyses;
        this.citations = citations;
        this.policyChunks = policyChunks;
        this.customers = customers;
        this.operators = operators;
        this.entityManager = entityManager;
        this.json = json;
    }

    /** Writes one analysis and its citations, and hands back what was actually stored. */
    @Transactional
    AiAnalysisView record(
            final UUID customerId,
            final String operatorUsername,
            final ActivityWindow window,
            final CustomerRiskReport report,
            final List<RetrievedChunk> policy,
            final LlmCompletion completion,
            final String provider,
            final String model) {
        final AiAnalysis analysis = analyses.saveAndFlush(
                row(customer(customerId), operator(operatorUsername), window, report, completion, provider, model));
        citations.saveAll(citationsOf(analysis, policy));
        entityManager.refresh(analysis);
        return viewOf(analysis, storedCitations(analysis.getAnalysisId()));
    }

    /** A customer's analyses, newest first — the order {@code idx_analysis_customer_time} exists for. */
    @Transactional(readOnly = true)
    List<AiAnalysisView> history(final UUID customerId) {
        return analyses.findByCustomerId(customerId).stream()
                .map(analysis -> viewOf(analysis, List.of()))
                .toList();
    }

    /** One analysis with the policy it was written from. */
    @Transactional(readOnly = true)
    AiAnalysisView find(final UUID analysisId) {
        final AiAnalysis analysis =
                analyses.findById(analysisId).orElseThrow(() -> new AiAnalysisNotFoundException(analysisId));
        return viewOf(analysis, storedCitations(analysisId));
    }

    /**
     * Resolved here rather than handed in, so the entity the row points at is managed by the same
     * transaction that writes the row. Existence was already established upstream — the evaluation
     * cannot have produced a report for a customer that is not there — so this failing means the
     * customer disappeared mid-analysis.
     */
    private Customer customer(final UUID customerId) {
        return customers
                .findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(String.valueOf(customerId)));
    }

    private Operator operator(final String username) {
        return operators
                .findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("authenticated operator " + username + " has no row"));
    }

    private AiAnalysis row(
            final Customer customer,
            final Operator operator,
            final ActivityWindow window,
            final CustomerRiskReport report,
            final LlmCompletion completion,
            final String provider,
            final String model) {
        final AnalysisVerdict verdict = completion.verdict();
        return new AiAnalysis(
                UUID.randomUUID(),
                customer,
                operator,
                OffsetDateTime.now(),
                window.from(),
                window.to(),
                report.score(),
                stored(report.level()),
                stored(verdict.assessedLevel()),
                verdict.summary(),
                json.writeValueAsString(verdict.recommendations()),
                provider,
                model,
                completion.promptVersion(),
                completion.inputTokens(),
                completion.outputTokens(),
                completion.latencyMs(),
                completion.rawResponse());
    }

    /**
     * The chunks in retrieval order, each pointing at the real {@code policy_chunks} row it came
     * from. Fetched in one query rather than one per citation — six primary-key lookups is six round
     * trips for no reason.
     */
    private List<AiAnalysisCitation> citationsOf(final AiAnalysis analysis, final List<RetrievedChunk> policy) {
        final Map<UUID, PolicyChunk> chunks =
                policyChunks
                        .findAllById(
                                policy.stream().map(RetrievedChunk::chunkId).toList())
                        .stream()
                        .collect(Collectors.toUnmodifiableMap(PolicyChunk::getChunkId, Function.identity()));
        return IntStream.range(0, policy.size())
                .filter(index -> chunks.containsKey(policy.get(index).chunkId()))
                .mapToObj(index -> new AiAnalysisCitation(
                        analysis,
                        chunks.get(policy.get(index).chunkId()),
                        (float) policy.get(index).similarity(),
                        (short) (index + 1)))
                .toList();
    }

    private List<PolicyCitationView> storedCitations(final UUID analysisId) {
        return citations.findByAnalysisId(analysisId).stream()
                .map(AiAnalysisArchive::citationView)
                .toList();
    }

    private static PolicyCitationView citationView(final AiAnalysisCitation citation) {
        final PolicyChunk chunk = citation.getChunk();
        return new PolicyCitationView(
                chunk.getChunkId(),
                chunk.getDocument(),
                chunk.getTitle(),
                chunk.getSection(),
                chunk.getBody(),
                citation.getSimilarity(),
                citation.getRank());
    }

    private AiAnalysisView viewOf(final AiAnalysis analysis, final List<PolicyCitationView> citationViews) {
        return new AiAnalysisView(
                analysis.getAnalysisId(),
                CustomerSearchService.viewOf(analysis.getCustomer()),
                operatorRef(analysis.getRequestedBy()),
                analysis.getCreatedAt(),
                analysis.getWindowFrom(),
                analysis.getWindowTo(),
                analysis.getComputedScore(),
                reported(analysis.getComputedLevel()),
                reported(analysis.getAssessedLevel()),
                analysis.isLevelsDiverged(),
                analysis.getSummary(),
                json.readValue(analysis.getRecommendations(), RECOMMENDATIONS),
                analysis.getProvider(),
                analysis.getModel(),
                analysis.getPromptVersion(),
                analysis.getInputTokens(),
                analysis.getOutputTokens(),
                analysis.getLatencyMs(),
                citationViews);
    }

    private static OperatorRef operatorRef(final Operator operator) {
        return new OperatorRef(operator.getOperatorId(), operator.getUsername(), operator.getDisplayName());
    }

    /**
     * The two {@code RiskLevel} enums are the same scale declared twice — once in the pure core, once
     * against the {@code CHECK} constraint the column carries. They are matched by name, which is
     * exactly what the column stores.
     */
    private static ch.gotthard.domain.model.RiskLevel stored(final RiskLevel level) {
        return ch.gotthard.domain.model.RiskLevel.valueOf(level.name());
    }

    private static RiskLevel reported(final ch.gotthard.domain.model.RiskLevel level) {
        return RiskLevel.valueOf(level.name());
    }
}
