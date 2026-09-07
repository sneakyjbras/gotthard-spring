package ch.gotthard.ai.analysis;

/**
 * The one way this application talks to a language model.
 *
 * <p>A port with two adapters: {@code AnthropicLlmClient} calls Claude, {@code StubLlmClient}
 * answers from the signals alone with no network and no key. Which one is wired is decided once, in
 * {@link LlmClientConfiguration}, and nothing above this interface can tell the difference — which
 * is what lets the whole analysis use case, its persistence and its endpoints be tested end to end
 * without a key, a bill, or a flaky third party in the build.
 *
 * <p>The port takes an {@link AnalysisRequest} rather than a finished prompt on purpose. Prompt
 * assembly is an implementation concern of talking to a model, not of the use case that wants an
 * analysis; a caller that had to build the prompt would be deciding for both adapters at once.
 */
public interface LlmClient {

    /**
     * The model's verdict on one customer's window, with everything worth recording about the call
     * that produced it.
     *
     * @throws LlmRefusedException if the model declined to answer, or the provider could not be
     *     reached — no analysis is written in either case
     */
    LlmCompletion analyse(AnalysisRequest request);

    /** {@code anthropic} or {@code stub}; recorded on every row so a summary is never mistaken for the other's. */
    String provider();

    /** The model identifier recorded alongside the provider. */
    String model();
}
