package ch.gotthard.service;

import ch.gotthard.ai.analysis.AnalysedCustomer;
import ch.gotthard.ai.analysis.AnalysisRequest;
import ch.gotthard.ai.analysis.ChannelLine;
import ch.gotthard.ai.analysis.ComputedRisk;
import ch.gotthard.ai.analysis.FiredRule;
import ch.gotthard.ai.retrieval.RetrievedChunk;
import ch.gotthard.domain.model.RiskRule;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Translates what the use cases already produced into the shape {@code ai/} accepts.
 *
 * <p>The whole of the boundary between the use case layer and the AI layer is this one class. {@code
 * ai/} sits below {@code service/} and cannot import {@link CustomerRiskReport}; rather than weaken
 * that, the mapping is written down once, here, where it is also the natural place to decide what
 * the model is and is not shown.
 *
 * <p><b>Findings are collapsed per rule.</b> {@link CustomerRiskReport#findings()} is one entry per
 * rule per transaction, which for a structuring run is the same rule five times over. A model shown
 * that reads five separate problems; a model shown "R-01 fired five times, contributing 44" reads
 * one pattern, which is what it is. The count and the total are kept, so nothing is lost.
 */
final class AnalysisRequestFactory {

    private AnalysisRequestFactory() {}

    static AnalysisRequest of(
            final CustomerRiskReport report,
            final ActivityOverview overview,
            final RuleCatalogue catalogue,
            final List<RetrievedChunk> policy) {
        return new AnalysisRequest(
                customer(report.customer()),
                report.from(),
                report.to(),
                new ComputedRisk(report.score(), report.level(), report.transactionsEvaluated()),
                activity(overview),
                firedRules(report, catalogue),
                policy);
    }

    private static AnalysedCustomer customer(final CustomerView customer) {
        return new AnalysedCustomer(customer.reference(), customer.fullName(), customer.country(), customer.segment());
    }

    private static List<ChannelLine> activity(final ActivityOverview overview) {
        return overview.channels().stream()
                .map(channel -> new ChannelLine(
                        channel.channel().name(),
                        channel.transactionCount(),
                        channel.volume().currency(),
                        channel.volume().amount(),
                        channel.unsuccessfulCount(),
                        channel.firstAt(),
                        channel.lastAt()))
                .toList();
    }

    /** Grouped by rule code, then ordered by what each rule contributed — loudest first. */
    private static List<FiredRule> firedRules(final CustomerRiskReport report, final RuleCatalogue catalogue) {
        return report.findings().stream().collect(Collectors.groupingBy(RiskFinding::ruleCode)).values().stream()
                .map(findings -> collapse(findings, catalogue))
                .sorted(Comparator.comparing(FiredRule::totalContribution).reversed())
                .toList();
    }

    private static FiredRule collapse(final List<RiskFinding> findings, final RuleCatalogue catalogue) {
        final RiskFinding first = findings.get(0);
        return new FiredRule(
                first.ruleCode(),
                first.ruleName(),
                thresholdLogic(first.ruleCode(), catalogue),
                findings.size(),
                findings.stream().map(RiskFinding::contribution).reduce(BigDecimal.ZERO, BigDecimal::add),
                findings.stream()
                        .map(RiskFinding::occurredAt)
                        .max(Comparator.naturalOrder())
                        .orElseThrow());
    }

    /**
     * The rule's condition in the words a compliance officer wrote. A rule that scored always has a
     * catalogue row — {@link RuleCatalogue#require} is what says so — but the prompt is not worth
     * failing an analysis over, so an unexpected gap degrades to a plain statement instead.
     */
    private static String thresholdLogic(final String ruleCode, final RuleCatalogue catalogue) {
        return catalogue
                .rule(ruleCode)
                .map(RiskRule::getThresholdLogic)
                .orElse("(no threshold logic recorded for " + ruleCode + ")");
    }
}
