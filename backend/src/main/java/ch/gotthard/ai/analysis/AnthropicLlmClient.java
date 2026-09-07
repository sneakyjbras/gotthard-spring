package ch.gotthard.ai.analysis;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * {@link LlmClient} backed by Claude, through the official SDK.
 *
 * <p><b>Structured output, never prose.</b> {@link AnalysisVerdict} is handed to {@code
 * outputConfig(Class)}, which derives a JSON schema from the record and constrains generation with
 * {@code output_config.format}. There is no parser in this class and there is not meant to be one: a
 * risk level scraped out of a paragraph is a level that can be scraped wrongly, and this one is
 * written to an audit table.
 *
 * <p><b>Adaptive thinking.</b> {@code ThinkingConfigAdaptive} lets the model decide how much
 * reasoning the case deserves — a clean customer with nothing fired should be cheap, a five-rule
 * structuring run should not be. A fixed {@code budget_tokens} is not merely worse here, it is
 * rejected outright on Opus 5.
 *
 * <p><b>Caching is why the prompt is sent in three pieces.</b> The instructions and the retrieved
 * policy go in {@code system}, in that order, with the cache breakpoint on the policy block; the
 * volatile signals go in the user message, after it. Analysing the same customer twice — which is
 * exactly what an operator does while working a case — then re-reads the whole prefix instead of
 * paying for it again.
 */
final class AnthropicLlmClient implements LlmClient {

    static final String PROVIDER = "anthropic";

    private final AnthropicClient claude;
    private final String model;
    private final long maxTokens;

    AnthropicLlmClient(final AnthropicClient claude, final String model, final int maxTokens) {
        this.claude = claude;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public LlmCompletion analyse(final AnalysisRequest request) {
        final AnalysisPrompt prompt = AnalysisPromptAssembler.assemble(request);
        final long startedAt = System.nanoTime();
        final StructuredMessage<AnalysisVerdict> message = send(prompt);
        final int latencyMs = millisSince(startedAt);
        refuseIfDeclined(message);
        return new LlmCompletion(
                verdictOf(message),
                prompt.version(),
                rawJson(message),
                Math.toIntExact(message.usage().inputTokens()),
                Math.toIntExact(message.usage().outputTokens()),
                latencyMs);
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String model() {
        return model;
    }

    /**
     * Every SDK failure — a bad key, a rate limit, a dead socket — becomes the same refusal the use
     * case already knows how to survive. The alternative is a 500 for something entirely routine.
     */
    private StructuredMessage<AnalysisVerdict> send(final AnalysisPrompt prompt) {
        try {
            return claude.messages().create(params(prompt));
        } catch (final AnthropicException failure) {
            throw new LlmRefusedException("Claude could not be reached: " + failure.getMessage(), failure);
        }
    }

    private StructuredMessageCreateParams<AnalysisVerdict> params(final AnalysisPrompt prompt) {
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder().text(prompt.instructions()).build(),
                        TextBlockParam.builder()
                                .text(prompt.policy())
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()))
                .outputConfig(AnalysisVerdict.class)
                .addUserMessage(prompt.signals())
                .build();
    }

    /**
     * A refusal arrives as a perfectly successful HTTP 200 with {@code stop_reason: "refusal"} and
     * no verdict in it. Read the content without checking this and the failure is a confusing
     * {@code NoSuchElementException} several frames away from the cause.
     */
    private static void refuseIfDeclined(final StructuredMessage<AnalysisVerdict> message) {
        if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            throw new LlmRefusedException("Claude declined to analyse this activity" + declineReason(message));
        }
    }

    private static String declineReason(final StructuredMessage<AnalysisVerdict> message) {
        return message.stopDetails()
                .map(details -> " (%s: %s)"
                        .formatted(
                                details.category().map(Object::toString).orElse("no category"),
                                details.explanation().orElse("no explanation")))
                .orElse("");
    }

    private static AnalysisVerdict verdictOf(final StructuredMessage<AnalysisVerdict> message) {
        return message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(StructuredTextBlock::text)
                .findFirst()
                .orElseThrow(() -> new LlmRefusedException("Claude returned no verdict block (stop reason %s)"
                        .formatted(message.stopReason().map(Object::toString).orElse("absent"))));
    }

    /**
     * The provider's own message, serialised with the SDK's mapper rather than ours — it is the only
     * one that knows how these Kotlin unions are shaped. Serialisation failing must not lose an
     * analysis that succeeded, so the fallback records why the raw response is missing.
     */
    private static String rawJson(final StructuredMessage<AnalysisVerdict> message) {
        try {
            return ObjectMappers.jsonMapper().writeValueAsString(message.rawMessage());
        } catch (final RuntimeException | com.fasterxml.jackson.core.JsonProcessingException notSerialisable) {
            return "{\"error\":\"raw response could not be serialised\"}";
        }
    }

    private static int millisSince(final long startedAtNanos) {
        return Math.toIntExact(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos));
    }
}
