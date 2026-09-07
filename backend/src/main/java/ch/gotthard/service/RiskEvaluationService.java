package ch.gotthard.service;

import ch.gotthard.core.model.RiskScore;
import ch.gotthard.core.risk.RiskScorer;
import ch.gotthard.core.risk.RuleHit;
import ch.gotthard.core.risk.RuleWeights;
import ch.gotthard.domain.model.RiskAssessment;
import ch.gotthard.domain.model.RiskRule;
import ch.gotthard.domain.model.Transaction;
import ch.gotthard.domain.query.FeatureWindowQuery;
import ch.gotthard.domain.query.NearThresholdBand;
import ch.gotthard.domain.query.RecordedAssessmentQuery;
import ch.gotthard.domain.repository.RiskAssessmentRepository;
import ch.gotthard.domain.repository.RiskRuleRepository;
import ch.gotthard.domain.repository.TransactionRepository;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Score a customer's window against the rules, and write down what fired.
 *
 * <p>Three steps, and the order matters. PostgreSQL computes the aggregates; the assembler turns
 * them into {@code Features}; the pure core decides. Nothing in this class knows what makes a
 * transaction risky, and nothing in the core knows where the numbers came from — the decision lives
 * in one place and it is not this one.
 *
 * <p><b>Every rule that fires writes a {@code risk_assessments} row.</b> That table is the audit
 * trail, so the write is not conditional on anyone asking for it. It is, however, idempotent: a
 * finding already recorded for a transaction is not recorded again, which is what lets an operator
 * refresh the screen without the trail growing a second copy of the same conclusion.
 *
 * <p>An empty {@code risk_rules} table is not an error. Every rule is then unweighted, the core
 * treats an unweighted rule as switched off, and the result is a score of nought with no findings —
 * which is exactly what "no rules configured" should mean.
 */
@Service
public class RiskEvaluationService {

    private final CustomerSearchService customers;
    private final FeatureWindowQuery featureWindow;
    private final FeatureAssembler assembler;
    private final RiskScorer scorer;
    private final NearThresholdBand nearThresholdBand;
    private final RiskRuleRepository riskRules;
    private final TransactionRepository transactions;
    private final RiskAssessmentRepository assessments;
    private final RecordedAssessmentQuery recordedAssessments;
    private final WalletProximityLoader walletProximity;

    public RiskEvaluationService(
            final CustomerSearchService customers,
            final FeatureWindowQuery featureWindow,
            final FeatureAssembler assembler,
            final RiskScorer scorer,
            final NearThresholdBand nearThresholdBand,
            final RiskRuleRepository riskRules,
            final TransactionRepository transactions,
            final RiskAssessmentRepository assessments,
            final RecordedAssessmentQuery recordedAssessments,
            final WalletProximityLoader walletProximity) {
        this.customers = customers;
        this.featureWindow = featureWindow;
        this.assembler = assembler;
        this.scorer = scorer;
        this.nearThresholdBand = nearThresholdBand;
        this.riskRules = riskRules;
        this.transactions = transactions;
        this.assessments = assessments;
        this.recordedAssessments = recordedAssessments;
        this.walletProximity = walletProximity;
    }

    @Transactional
    public CustomerRiskReport evaluate(final UUID customerId, final ActivityWindow window) {
        final CustomerView customer = customers.findById(customerId);
        final RuleCatalogue catalogue = RuleCatalogue.of(riskRules.findAll());
        final List<ScoredActivity> scored = score(customerId, window, catalogue);
        writeAuditTrail(scored, catalogue);
        return report(customer, window, scored, catalogue);
    }

    private List<ScoredActivity> score(
            final UUID customerId, final ActivityWindow window, final RuleCatalogue catalogue) {
        final RuleWeights weights = catalogue.weights();
        final WalletProximity proximity = walletProximity.load();
        return featureWindow
                .findFeatures(customerId, window.from(), window.to(), ReportingRates.conversion(), nearThresholdBand)
                .stream()
                .map(row -> new ScoredActivity(row, scorer.score(assembler.assemble(row, proximity), weights)))
                .toList();
    }

    private void writeAuditTrail(final List<ScoredActivity> scored, final RuleCatalogue catalogue) {
        final List<ScoredActivity> fired =
                scored.stream().filter(ScoredActivity::fired).toList();
        if (!fired.isEmpty()) {
            // Flushed rather than left to the commit: the trail is written at the moment the rules
            // decided, so a second evaluation in the same transaction sees it and does not repeat it.
            assessments.saveAllAndFlush(unrecorded(fired, catalogue));
        }
    }

    private List<RiskAssessment> unrecorded(final List<ScoredActivity> fired, final RuleCatalogue catalogue) {
        final Map<UUID, Set<UUID>> already = recordedAssessments.findRecordedRuleIds(transactionIds(fired));
        final Map<UUID, Transaction> byId = transactionsById(transactionIds(fired));
        final OffsetDateTime triggeredAt = OffsetDateTime.now();
        return fired.stream()
                .flatMap(activity -> newAssessments(activity, catalogue, already, byId, triggeredAt))
                .toList();
    }

    private static Stream<RiskAssessment> newAssessments(
            final ScoredActivity activity,
            final RuleCatalogue catalogue,
            final Map<UUID, Set<UUID>> already,
            final Map<UUID, Transaction> byId,
            final OffsetDateTime triggeredAt) {
        final Set<UUID> recorded = already.getOrDefault(activity.transactionId(), Set.of());
        return activity.evaluation().hits().stream()
                .map(hit -> new ScoredRule(catalogue.require(hit.ruleCode()), hit))
                .filter(scored -> !recorded.contains(scored.rule().getRuleId()))
                .map(scored -> new RiskAssessment(
                        UUID.randomUUID(),
                        byId.get(activity.transactionId()),
                        scored.rule(),
                        triggeredAt,
                        scored.hit().contribution()));
    }

    private Map<UUID, Transaction> transactionsById(final List<UUID> transactionIds) {
        return transactions.findAllById(transactionIds).stream()
                .collect(Collectors.toUnmodifiableMap(Transaction::getTransactionId, Function.identity()));
    }

    private static List<UUID> transactionIds(final List<ScoredActivity> scored) {
        return scored.stream().map(ScoredActivity::transactionId).toList();
    }

    private static CustomerRiskReport report(
            final CustomerView customer,
            final ActivityWindow window,
            final List<ScoredActivity> scored,
            final RuleCatalogue catalogue) {
        final RiskScore highest = highestScore(scored);
        return new CustomerRiskReport(
                customer,
                window.from(),
                window.to(),
                scored.size(),
                highest.value(),
                highest.level(),
                findings(scored, catalogue));
    }

    /** A customer is as risky as their riskiest activity — see {@link CustomerRiskReport}. */
    private static RiskScore highestScore(final List<ScoredActivity> scored) {
        return scored.stream()
                .map(activity -> activity.evaluation().score())
                .max(Comparator.comparing(RiskScore::value))
                .orElseGet(RiskScore::zero);
    }

    private static List<RiskFinding> findings(final List<ScoredActivity> scored, final RuleCatalogue catalogue) {
        return scored.stream()
                .filter(ScoredActivity::fired)
                .sorted(Comparator.comparing(ScoredActivity::occurredAt).reversed())
                .flatMap(activity -> findingsOf(activity, catalogue))
                .toList();
    }

    private static Stream<RiskFinding> findingsOf(final ScoredActivity activity, final RuleCatalogue catalogue) {
        return activity.evaluation().hits().stream().map(hit -> findingOf(activity, hit, catalogue));
    }

    /**
     * A finding carries the rule's condition as well as its name. The operator seeing a score needs
     * to read why it moved, and the sentence explaining that already exists in {@code
     * risk_rules.threshold_logic} — sending only the code would force every client to keep its own
     * copy of it.
     */
    private static RiskFinding findingOf(
            final ScoredActivity activity, final RuleHit hit, final RuleCatalogue catalogue) {
        final RiskRule rule = catalogue.require(hit.ruleCode());
        return new RiskFinding(
                activity.transactionId(),
                activity.occurredAt(),
                activity.channel(),
                hit.ruleCode(),
                rule.getRuleName(),
                rule.getThresholdLogic(),
                hit.contribution());
    }

    /** A hit paired with the row that priced it, so the assessment can be written in one pass. */
    private record ScoredRule(RiskRule rule, RuleHit hit) {}
}
