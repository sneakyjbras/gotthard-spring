package ch.gotthard.ai.analysis;

import ch.gotthard.core.model.RiskLevel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link LlmClient} that answers from the signals alone: no key, no network, no bill, same shape.
 *
 * <p>This is not a mock and it is not test scaffolding. It is the adapter the application actually
 * runs with when nobody has exported an {@code ANTHROPIC_API_KEY}, which means a clean checkout
 * demonstrates the whole feature — prompt, verdict, persistence, citations, history — end to end,
 * and the entire test suite exercises the real use case rather than a stand-in for it.
 *
 * <p><b>It assembles the same prompt.</b> Nothing here needs the text, but building it means prompt
 * assembly is covered by every test that runs an analysis, and the recorded {@code prompt_version}
 * is the truth for both adapters rather than a constant this one happens to repeat.
 *
 * <p><b>It is allowed to disagree, and it will.</b> Its level comes from how many <em>distinct</em>
 * rules fired, while the computed level comes from their weighted score. Those two are genuinely
 * different questions — one loud rule outscores three quiet ones — so divergence arises here for the
 * same reason it arises with a real model: a second reading of the same evidence emphasising
 * something else. A stub that simply echoed the computed level would make {@code levels_diverged}
 * untestable and, worse, look correct while proving nothing.
 */
final class StubLlmClient implements LlmClient {

    static final String PROVIDER = "stub";
    static final String MODEL = "stub-analyst-v1";

    /** What a firing of each rule actually asks a human to go and do. Keyed by rule code, cited to its own policy. */
    private static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry(
                    "R-01",
                    "File a suspicious activity report for near-threshold structuring, citing AML-001 §3, and"
                            + " attach the transaction list for the seven-day window."),
            Map.entry(
                    "R-02",
                    "Obtain the purpose and the ultimate beneficiary of the cross-border transfers before"
                            + " releasing any further payment on this corridor (SANCTIONS-01 §4)."),
            Map.entry(
                    "R-03",
                    "Block the card and issue a replacement: the decline cluster matches card testing as"
                            + " described in FRAUD-01 §3."),
            Map.entry(
                    "R-04",
                    "Ask the customer to evidence the source of the assets disposed of through the exchange"
                            + " (CRYPTO-01 §3)."),
            Map.entry(
                    "R-05",
                    "Escalate to the blockchain analytics desk — the sending wallet is within two hops of a"
                            + " flagged address (CRYPTO-02 §3)."),
            Map.entry(
                    "R-06",
                    "Re-verify the account holder out of band before releasing further payments; a long"
                            + " dormancy ending in a burst is the shape of an account takeover (AML-002 §3)."),
            Map.entry(
                    "R-07",
                    "Review the quasi-cash and gambling spend against the customer's stated profile"
                            + " (AML-003 §3)."));

    private static final String CLOSING_ACTION =
            "Record the outcome of this review on the case file, whichever way it goes.";

    /** The usual rule of thumb for English prose; the stub reports an estimate, not a meter reading. */
    private static final int CHARACTERS_PER_TOKEN = 4;

    private final ObjectMapper json;

    StubLlmClient(final ObjectMapper json) {
        this.json = json;
    }

    @Override
    public LlmCompletion analyse(final AnalysisRequest request) {
        final long startedAt = System.nanoTime();
        final AnalysisPrompt prompt = AnalysisPromptAssembler.assemble(request);
        final AnalysisVerdict verdict = new AnalysisVerdict(assess(request), summarise(request), recommend(request));
        return new LlmCompletion(
                verdict,
                prompt.version(),
                rawJson(prompt, verdict),
                estimatedTokens(prompt.rendered()),
                estimatedTokens(verdict.summary() + String.join(" ", verdict.recommendations())),
                millisSince(startedAt));
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String model() {
        return MODEL;
    }

    /**
     * How many separate things went wrong, rather than how heavily they were weighted. One pattern
     * is a question, two is a case, three or more is a customer behaving in several ways at once.
     */
    private static RiskLevel assess(final AnalysisRequest request) {
        return switch (Math.min(request.firedRules().size(), 3)) {
            case 0 -> RiskLevel.LOW;
            case 1 -> RiskLevel.MEDIUM;
            case 2 -> RiskLevel.HIGH;
            default -> RiskLevel.CRITICAL;
        };
    }

    private static String summarise(final AnalysisRequest request) {
        return request.firedRules().isEmpty() ? quietSummary(request) : firedSummary(request);
    }

    private static String quietSummary(final AnalysisRequest request) {
        return ("%s shows no rule-level concern across %d transaction(s) in the window analysed. "
                        + "The deterministic engine scored the window %s (%s) and nothing in the activity "
                        + "profile contradicts that. No policy was retrieved, because no rule fired.")
                .formatted(
                        request.customer().fullName(),
                        request.computed().transactionsEvaluated(),
                        request.computed().score().toPlainString(),
                        request.computed().level());
    }

    private static String firedSummary(final AnalysisRequest request) {
        return ("%s tripped %d rule(s) across %d transaction(s) in the window analysed: %s. "
                        + "The deterministic engine scored the window %s (%s); read on the number of distinct "
                        + "patterns rather than their weights, this reads as %s. "
                        + "%d policy extract(s) were retrieved for the rules that fired and are cited on this analysis.")
                .formatted(
                        request.customer().fullName(),
                        request.firedRules().size(),
                        request.computed().transactionsEvaluated(),
                        namedRules(request),
                        request.computed().score().toPlainString(),
                        request.computed().level(),
                        assess(request),
                        request.policy().size());
    }

    private static String namedRules(final AnalysisRequest request) {
        return request.firedRules().stream()
                .map(rule -> "%s %s (%s)".formatted(rule.ruleCode(), rule.ruleName(), occurrences(rule)))
                .collect(Collectors.joining(", "));
    }

    private static String occurrences(final FiredRule rule) {
        return rule.occurrences() == 1 ? "once" : rule.occurrences() + " times";
    }

    private static List<String> recommend(final AnalysisRequest request) {
        return Stream.concat(
                        request.firedRules().stream()
                                .map(FiredRule::ruleCode)
                                .map(code -> ACTIONS.getOrDefault(code, fallbackAction(code))),
                        Stream.of(CLOSING_ACTION))
                .distinct()
                .toList();
    }

    private static String fallbackAction(final String ruleCode) {
        return "Review the activity that triggered %s against the corresponding policy.".formatted(ruleCode);
    }

    /**
     * The stub's own answer, in the same place a provider's would be stored — so a reader of {@code
     * ai_analyses.raw_response} is never left guessing which adapter wrote the row.
     */
    private String rawJson(final AnalysisPrompt prompt, final AnalysisVerdict verdict) {
        return json.writeValueAsString(new StubResponse(PROVIDER, MODEL, prompt.version(), verdict));
    }

    /** Four characters to the token: near enough to be useful on a dashboard, and never mistaken for a bill. */
    private static Integer estimatedTokens(final String text) {
        return Math.ceilDiv(text.length(), CHARACTERS_PER_TOKEN);
    }

    private static int millisSince(final long startedAtNanos) {
        return Math.toIntExact(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos));
    }

    private record StubResponse(String provider, String model, String promptVersion, AnalysisVerdict verdict) {}
}
