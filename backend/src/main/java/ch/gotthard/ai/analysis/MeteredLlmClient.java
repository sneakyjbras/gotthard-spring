package ch.gotthard.ai.analysis;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;

/**
 * Publishes what an analysis cost, without the analysis knowing it is being measured.
 *
 * <p>Token counts already reach {@code ai_analyses}, but a row in a table answers "what did this
 * one cost" and not "what are we spending". Cost control on a model call is an operational concern
 * at a bank, so the counts are published to Micrometer as well, where they can be summed over time
 * and alerted on.
 *
 * <p>A decorator rather than a dependency of {@link ch.gotthard.service.AiAnalysisService}: the
 * service orchestrates an analysis and measuring it is a different job. Wrapping keeps the service
 * unchanged, keeps this class to one responsibility, and means a deployment that wanted no metrics
 * would simply not apply the wrapper.
 */
public final class MeteredLlmClient implements LlmClient {

    private static final String TOKENS = "gotthard.ai.tokens";
    private static final String ANALYSES = "gotthard.ai.analysis";

    private final LlmClient delegate;
    private final MeterRegistry meters;

    public MeteredLlmClient(final LlmClient delegate, final MeterRegistry meters) {
        this.delegate = delegate;
        this.meters = meters;
    }

    /**
     * Times the call, counts its outcome, and records the tokens it reported.
     *
     * <p>A refusal is an outcome worth counting, not an error to swallow — it is measured and then
     * rethrown untouched, so the caller's behaviour is exactly as if this wrapper were absent.
     */
    @Override
    public LlmCompletion analyse(final AnalysisRequest request) {
        final Timer.Sample sample = Timer.start(meters);
        try {
            final LlmCompletion completion = delegate.analyse(request);
            recordTokens(completion);
            finish(sample, "success");
            return completion;
        } catch (final LlmRefusedException refused) {
            finish(sample, "refused");
            throw refused;
        }
    }

    /** Absent counts are left absent — a zero would read as "cost nothing", which is not the claim. */
    private void recordTokens(final LlmCompletion completion) {
        countTokens("input", completion.inputTokens());
        countTokens("output", completion.outputTokens());
    }

    private void countTokens(final String kind, final Integer count) {
        Optional.ofNullable(count).ifPresent(tokens -> Counter.builder(TOKENS)
                .description("Tokens billed by the analysis model, by direction")
                .baseUnit("tokens")
                .tag("token_type", kind)
                .tag("provider", provider())
                .tag("model", model())
                .register(meters)
                .increment(tokens));
    }

    private void finish(final Timer.Sample sample, final String outcome) {
        sample.stop(Timer.builder(ANALYSES)
                .description("Analyses requested of the model, and how long they took")
                .tag("outcome", outcome)
                .tag("provider", provider())
                .tag("model", model())
                .register(meters));
    }

    @Override
    public String provider() {
        return delegate.provider();
    }

    @Override
    public String model() {
        return delegate.model();
    }
}
