package ch.gotthard.ai.analysis;

/**
 * One answered call: what the model said, and what the call cost.
 *
 * <p>Everything but {@link #verdict} is provenance, and it is on the port rather than left to the
 * caller to guess because only the adapter knows it. An analysis is an audit record; six months
 * later "which model, under which prompt, how long did it take and how many tokens did it burn" are
 * the questions actually asked of it, and none of them can be reconstructed after the fact.
 *
 * @param rawResponse the provider's own JSON, stored verbatim in {@code ai_analyses.raw_response}
 * @param inputTokens null when the provider does not report usage
 */
public record LlmCompletion(
        AnalysisVerdict verdict,
        String promptVersion,
        String rawResponse,
        Integer inputTokens,
        Integer outputTokens,
        int latencyMs) {}
