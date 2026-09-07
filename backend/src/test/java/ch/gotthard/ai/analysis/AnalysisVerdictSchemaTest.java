package ch.gotthard.ai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.core.model.RiskLevel;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import org.junit.jupiter.api.Test;

/**
 * The structured-output contract, checked offline.
 *
 * <p>{@link AnalysisVerdict} only ever becomes a JSON schema inside a real API call, which the test
 * suite deliberately never makes. That would leave the one part of the contract that cannot be
 * fixed at run time — whether the SDK can derive a schema from this record at all — unverified until
 * someone ran the application with a key. Building the parameters exercises exactly that derivation
 * and validation, with no network and no key: {@code outputConfig(Class)} is where the schema is
 * produced and locally validated.
 */
class AnalysisVerdictSchemaTest {

    @Test
    void given_theVerdictRecord_when_usedAsAStructuredOutput_then_theSdkDerivesASchemaForIt() {
        final StructuredMessageCreateParams<AnalysisVerdict> params = MessageCreateParams.builder()
                .model("claude-opus-5")
                .maxTokens(1024L)
                .outputConfig(AnalysisVerdict.class)
                .addUserMessage("unused — this test never sends the request")
                .build();

        assertThat(params.outputType()).isEqualTo(AnalysisVerdict.class);
        assertThat(params.rawParams().outputConfig()).isPresent();
    }

    @Test
    void given_theVerdictRecord_when_theSchemaIsRendered_then_itConstrainsTheLevelToTheKnownBands() {
        final String schema = MessageCreateParams.builder()
                .model("claude-opus-5")
                .maxTokens(1024L)
                .outputConfig(AnalysisVerdict.class)
                .addUserMessage("unused")
                .build()
                .rawParams()
                .outputConfig()
                .orElseThrow()
                .toString();

        assertThat(schema).contains("assessedLevel", "summary", "recommendations");
        for (final RiskLevel level : RiskLevel.values()) {
            assertThat(schema).contains(level.name());
        }
    }
}
