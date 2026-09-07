package ch.gotthard.ai.analysis;

/**
 * One assembled prompt, split at the two seams that matter for caching rather than kept as a single
 * string.
 *
 * <p>The API caches by prefix: any byte that changes invalidates everything after it. So the parts
 * are stored — and sent — most stable first. {@link #instructions} is a constant of this build.
 * {@link #policy} changes only when a different set of rules fires, which for one customer under
 * review is rarely. {@link #signals} carries the window, the figures and the timestamps, and changes
 * on every single call. Sending them in that order, with the cache breakpoint after the policy,
 * means a second analysis of the same customer re-reads the first two rather than paying for them
 * again.
 *
 * @param version {@link AnalysisPromptAssembler#VERSION} at the moment of assembly, carried through
 *     to {@code ai_analyses.prompt_version} so a stored analysis can always be traced to the prompt
 *     that produced it
 */
public record AnalysisPrompt(String version, String instructions, String policy, String signals) {

    /** The whole prompt as one string — what an adapter with no notion of caching sends. */
    public String rendered() {
        return instructions + "\n\n" + policy + "\n\n" + signals;
    }
}
