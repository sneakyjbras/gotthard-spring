package ch.gotthard.ai.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the analysis talks to a model, bound from {@code gotthard.ai.*}.
 *
 * <p>{@link #apiKey} is mapped from {@code ANTHROPIC_API_KEY} in {@code application.properties} and
 * defaults to empty. Empty is not a misconfiguration: it is the signal that selects the stub adapter
 * (see {@link LlmClientConfiguration}), which is why every default here leaves a fresh checkout
 * working with nothing set.
 *
 * @param topKPolicyChunks how many retrieved chunks are shown, and therefore cited. Enough to
 *     ground a paragraph on more than one clause, few enough that the prompt stays readable and the
 *     citation list is a claim about what the model saw rather than a dump of the corpus.
 * @param maxTokens generous: a refused or truncated verdict is worth far more than the tokens saved.
 */
@ConfigurationProperties(prefix = "gotthard.ai")
public record AiAnalysisProperties(String apiKey, String model, int topKPolicyChunks, int maxTokens) {

    public AiAnalysisProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null || model.isBlank() ? "claude-opus-5" : model;
        topKPolicyChunks = topKPolicyChunks <= 0 ? 6 : topKPolicyChunks;
        maxTokens = maxTokens <= 0 ? 4096 : maxTokens;
    }

    /** Whether a real key was supplied — the whole of the adapter choice. */
    public boolean hasApiKey() {
        return !apiKey.isEmpty();
    }
}
