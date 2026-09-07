package ch.gotthard.ai.analysis;

import ch.gotthard.ai.retrieval.RetrievedChunk;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Turns an {@link AnalysisRequest} into the three blocks of an {@link AnalysisPrompt}.
 *
 * <p><b>Order is a design decision, not a formatting one.</b> The instructions are a constant of
 * this build; the policy changes only when a different set of rules fires; the signals change on
 * every call. Written in that order, everything above the last cache breakpoint is re-readable, and
 * a second analysis of the same customer pays full price only for the part that actually moved. Put
 * the timestamps first instead and nothing would ever cache.
 *
 * <p><b>{@link #VERSION} is what makes a stored analysis reproducible.</b> Every row in {@code
 * ai_analyses} records the version of the prompt that produced it, so a summary written six weeks
 * ago can be read knowing what the model was actually asked. Change any of the text below and the
 * version has to change with it — otherwise two different questions are recorded under one answer.
 * It is capped at sixteen characters by {@code ai_analyses.prompt_version}.
 */
public final class AnalysisPromptAssembler {

    /** Bump on any change to the text below. Maximum sixteen characters — see the class Javadoc. */
    public static final String VERSION = "analysis-v1";

    /**
     * The standing instructions. Frozen for the life of {@link #VERSION}, which is what allows it to
     * sit first and be cached: it contains no customer, no timestamp and no figure.
     */
    private static final String INSTRUCTIONS =
            """
            You are a compliance analyst working inside Gotthard Bank's customer-activity console.

            A deterministic rule engine has already scored this customer's activity. Its score and its
            risk level are facts, and they are not yours to revise — the rules decide risk, you explain
            it. You are asked for three things, and nothing else.

            1. assessedLevel — your own independent read of the same evidence, on the scale
               LOW / MEDIUM / HIGH / CRITICAL. Say what you actually think. If it differs from the
               computed level, say so plainly in the summary and say why. A disagreement is recorded
               and reviewed by a human; it is a signal, not a mistake.
            2. summary — three to five sentences an operator can read aloud on a call. Name the
               pattern, the channel it ran through, and the figures that matter. No preamble, and no
               restating the input back at the reader.
            3. recommendations — two to five concrete next actions, imperative, one line each. Each
               one thing a human can do today: request a document, freeze a card, file a report,
               escalate to a named desk.

            Ground every claim in the evidence below. The policy extracts are the only policy you may
            rely on; cite them by document and section, as in "AML-001 §3". If the evidence does not
            support a claim, leave the claim out. Never invent a transaction, an amount, a
            counterparty, or a policy clause. An absence of evidence is worth saying out loud.
            """;

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private AnalysisPromptAssembler() {}

    /** The prompt for one customer's window: instructions, then policy, then the volatile signals. */
    public static AnalysisPrompt assemble(final AnalysisRequest request) {
        return new AnalysisPrompt(VERSION, INSTRUCTIONS, policy(request.policy()), signals(request));
    }

    /**
     * The retrieved chunks, numbered so the model can refer to one, and labelled with the fired rule
     * whose query found it — which is what makes "cite AML-001 §3" a checkable instruction rather
     * than an invitation to paraphrase.
     */
    private static String policy(final List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return "## Policy extracts\n\nNo rule fired, so no policy was retrieved. Do not cite any.";
        }
        return "## Policy extracts retrieved for the rules that fired\n\n"
                + IntStream.range(0, chunks.size())
                        .mapToObj(index -> citation(index + 1, chunks.get(index)))
                        .collect(Collectors.joining("\n\n"));
    }

    private static String citation(final int rank, final RetrievedChunk chunk) {
        return "[%d] %s §%s — %s (retrieved for %s, similarity %.3f)\n%s"
                .formatted(
                        rank,
                        chunk.document(),
                        chunk.section(),
                        chunk.title(),
                        chunk.firedRuleCode(),
                        chunk.similarity(),
                        chunk.body());
    }

    /** Everything that changes between two calls, kept together and kept last. */
    private static String signals(final AnalysisRequest request) {
        return String.join(
                "\n\n", customer(request), window(request), activity(request), rules(request), computed(request));
    }

    private static String customer(final AnalysisRequest request) {
        final AnalysedCustomer customer = request.customer();
        return "## Customer\n\n%s — %s, %s customer onboarded in %s"
                .formatted(customer.reference(), customer.fullName(), customer.segment(), customer.country());
    }

    private static String window(final AnalysisRequest request) {
        return "## Window analysed\n\n%s to %s, %d transactions evaluated"
                .formatted(
                        TIMESTAMP.format(request.windowFrom()),
                        TIMESTAMP.format(request.windowTo()),
                        request.computed().transactionsEvaluated());
    }

    private static String activity(final AnalysisRequest request) {
        if (request.activity().isEmpty()) {
            return "## Activity in the window\n\nNothing at all — the customer did not transact.";
        }
        return "## Activity in the window\n\n| channel | transactions | volume | unsuccessful | first | last |\n"
                + "|---|---|---|---|---|---|\n"
                + request.activity().stream()
                        .map(AnalysisPromptAssembler::activityRow)
                        .collect(Collectors.joining("\n"));
    }

    private static String activityRow(final ChannelLine line) {
        return "| %s | %d | %s %s | %d | %s | %s |"
                .formatted(
                        line.channel(),
                        line.transactionCount(),
                        line.currency(),
                        plain(line.volume()),
                        line.unsuccessfulCount(),
                        TIMESTAMP.format(line.firstAt()),
                        TIMESTAMP.format(line.lastAt()));
    }

    private static String rules(final AnalysisRequest request) {
        if (request.firedRules().isEmpty()) {
            return "## Rules that fired\n\nNone. Nothing in this window tripped a rule.";
        }
        return "## Rules that fired\n\n"
                + request.firedRules().stream()
                        .map(AnalysisPromptAssembler::firedRule)
                        .collect(Collectors.joining("\n\n"));
    }

    private static String firedRule(final FiredRule rule) {
        return "### %s %s\nFired on %d transaction(s), most recently %s, contributing %s to the score.\nCondition: %s"
                .formatted(
                        rule.ruleCode(),
                        rule.ruleName(),
                        rule.occurrences(),
                        TIMESTAMP.format(rule.lastFiredAt()),
                        plain(rule.totalContribution()),
                        rule.thresholdLogic());
    }

    private static String computed(final AnalysisRequest request) {
        final ComputedRisk computed = request.computed();
        return "## What the rules computed (a fact, not a question)\n\nScore %s, level %s."
                .formatted(plain(computed.score()), computed.level());
    }

    private static String plain(final BigDecimal amount) {
        return amount.toPlainString();
    }
}
