package ch.gotthard.service;

import ch.gotthard.ai.analysis.AiAnalysisProperties;
import ch.gotthard.ai.analysis.AnalysisRequest;
import ch.gotthard.ai.analysis.LlmClient;
import ch.gotthard.ai.analysis.LlmCompletion;
import ch.gotthard.ai.analysis.LlmRefusedException;
import ch.gotthard.ai.retrieval.KnowledgeRetriever;
import ch.gotthard.ai.retrieval.RetrievedChunk;
import ch.gotthard.domain.repository.RiskRuleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Ask a model to explain what the rules found, and keep the answer.
 *
 * <p>Five steps, and the order is the argument. The rules score the window and record what fired.
 * The codes that fired — never operator free text — become the retrieval query. The score, the
 * activity, the rules' own {@code threshold_logic} and the retrieved policy become the prompt. The
 * model answers in a fixed structure. The answer is written down beside the score it was explaining,
 * with every clause it was shown cited against it.
 *
 * <p><b>The model never decides risk.</b> Its level goes into a different column from the rules', and
 * {@code levels_diverged} exists so that a disagreement is visible rather than resolved. Nothing in
 * this class lets one overwrite the other, and nothing downstream reads the assessed level as though
 * it were the score.
 *
 * <p><b>Deliberately not {@code @Transactional} as a whole.</b> An LLM call takes seconds; holding a
 * database connection open across it to no purpose is how a connection pool is exhausted by a
 * feature nobody is even using yet. Each collaborator opens the transaction it needs — the rules
 * write their audit trail in theirs, {@link AiAnalysisArchive} writes the analysis in its own — and
 * the slow part happens between them, holding nothing.
 */
@Service
public class AiAnalysisService {

    private final CustomerSearchService customers;
    private final RiskEvaluationService riskEvaluation;
    private final ActivityOverviewService activityOverview;
    private final KnowledgeRetriever retriever;
    private final LlmClient llm;
    private final RiskRuleRepository riskRules;
    private final AiAnalysisArchive archive;
    private final AiAnalysisProperties properties;

    public AiAnalysisService(
            final CustomerSearchService customers,
            final RiskEvaluationService riskEvaluation,
            final ActivityOverviewService activityOverview,
            final KnowledgeRetriever retriever,
            final LlmClient llm,
            final RiskRuleRepository riskRules,
            final AiAnalysisArchive archive,
            final AiAnalysisProperties properties) {
        this.customers = customers;
        this.riskEvaluation = riskEvaluation;
        this.activityOverview = activityOverview;
        this.retriever = retriever;
        this.llm = llm;
        this.riskRules = riskRules;
        this.archive = archive;
        this.properties = properties;
    }

    /** Runs one analysis and records it against {@code operatorUsername}, who asked for it. */
    public AiAnalysisView analyse(final UUID customerId, final ActivityWindow window, final String operatorUsername) {
        final CustomerRiskReport report = riskEvaluation.evaluate(customerId, window);
        final List<RetrievedChunk> policy = groundingFor(report);
        return archive.record(
                customerId,
                operatorUsername,
                window,
                report,
                policy,
                answer(request(report, window, policy)),
                llm.provider(),
                llm.model());
    }

    /** A customer's analyses, newest first, without the policy text each was written from. */
    public List<AiAnalysisView> history(final UUID customerId) {
        customers.findById(customerId);
        return archive.history(customerId);
    }

    /** One analysis, with every policy chunk the model was shown. */
    public AiAnalysisView find(final UUID analysisId) {
        return archive.find(analysisId);
    }

    /**
     * Policy for the codes that fired, and nothing when nothing fired. A clean customer has no
     * finding to ground, so the honest prompt is one with no policy in it rather than whatever the
     * corpus happens to look like from a distance.
     */
    private List<RetrievedChunk> groundingFor(final CustomerRiskReport report) {
        return retriever.retrieve(firedRuleCodes(report), properties.topKPolicyChunks());
    }

    private static List<String> firedRuleCodes(final CustomerRiskReport report) {
        return report.findings().stream().map(RiskFinding::ruleCode).distinct().toList();
    }

    private AnalysisRequest request(
            final CustomerRiskReport report, final ActivityWindow window, final List<RetrievedChunk> policy) {
        return AnalysisRequestFactory.of(
                report,
                activityOverview.overview(report.customer().customerId(), window),
                RuleCatalogue.of(riskRules.findAll()),
                policy);
    }

    /**
     * A refusal is the model's answer, not a fault of this application — it becomes a use-case
     * complaint the web layer turns into a status, and nothing is written. See {@link
     * LlmRefusedException} for why a fabricated analysis would be the worse outcome.
     */
    private LlmCompletion answer(final AnalysisRequest request) {
        try {
            return llm.analyse(request);
        } catch (final LlmRefusedException refused) {
            throw new AiAnalysisUnavailableException(refused.getMessage(), refused);
        }
    }
}
