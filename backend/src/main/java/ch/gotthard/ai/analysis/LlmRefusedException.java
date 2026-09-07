package ch.gotthard.ai.analysis;

/**
 * The model produced no verdict — it declined, or the provider could not be reached.
 *
 * <p><b>Nothing is written when this is thrown.</b> A refusal could be papered over by storing the
 * computed level as though the model had agreed with it, and that would be the worst possible
 * outcome: {@code levels_diverged} would read {@code false} for an analysis the model never made,
 * and the one column whose entire purpose is to surface disagreement would be quietly lying. An
 * absent analysis is honest; a fabricated one is not.
 *
 * <p>Not fatal either. {@code AiAnalysisService} lets this surface as a refused request with a
 * status code and a message, the console says the model would not answer, and the operator still
 * has the rules' own verdict — which was never the model's to give in the first place.
 */
public class LlmRefusedException extends RuntimeException {

    public LlmRefusedException(final String message) {
        super(message);
    }

    public LlmRefusedException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
