package ch.gotthard.ai.analysis;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Which {@link LlmClient} the application runs with, decided once and announced in the log.
 *
 * <p><b>A key is present, so call Claude; no key, so answer offline.</b> That is the whole rule, and
 * it is spelled out as two mutually exclusive {@code @ConditionalOnExpression}s rather than one
 * conditional plus {@code @ConditionalOnMissingBean}: negated explicitly, neither bean depends on
 * the order Spring happens to evaluate the other in, and reading either method tells you exactly
 * when it applies.
 *
 * <p>The condition is on {@code gotthard.ai.api-key}, which {@code application.properties} maps from
 * the {@code ANTHROPIC_API_KEY} environment variable and defaults to empty. Going through a property
 * rather than reading the variable directly is what makes the choice testable: the suite sets that
 * property to empty and is guaranteed the stub, whether or not the machine running the build has a
 * key exported. A test that quietly picked up a developer's key would make real API calls, cost real
 * money, and fail in CI for reasons nobody could see in the diff.
 *
 * <p>Both branches log at INFO on startup. "Which model answered" is the first question anyone asks
 * of an AI feature, and it must not require reading the configuration to find out.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiAnalysisProperties.class)
public class LlmClientConfiguration {

    /** Kept as a constant so the stub's condition is visibly the negation of the adapter's, not a near-copy. */
    private static final String KEY_PRESENT = "!'${gotthard.ai.api-key:}'.isBlank()";

    private static final String KEY_ABSENT = "'${gotthard.ai.api-key:}'.isBlank()";

    private static final Logger log = LoggerFactory.getLogger(LlmClientConfiguration.class);

    @Bean
    @ConditionalOnExpression(KEY_PRESENT)
    LlmClient anthropicLlmClient(final AiAnalysisProperties properties) {
        log.info(
                "AI analysis provider: anthropic, model {}, prompt {} — ANTHROPIC_API_KEY is set, analyses will"
                        + " call the Claude API",
                properties.model(),
                AnalysisPromptAssembler.VERSION);
        return new AnthropicLlmClient(
                AnthropicOkHttpClient.builder().apiKey(properties.apiKey()).build(),
                properties.model(),
                properties.maxTokens());
    }

    @Bean
    @ConditionalOnExpression(KEY_ABSENT)
    LlmClient stubLlmClient(final ObjectMapper json) {
        log.info(
                "AI analysis provider: stub, model {}, prompt {} — no ANTHROPIC_API_KEY, analyses are derived"
                        + " deterministically from the signals and make no network call",
                StubLlmClient.MODEL,
                AnalysisPromptAssembler.VERSION);
        return new StubLlmClient(json);
    }
}
