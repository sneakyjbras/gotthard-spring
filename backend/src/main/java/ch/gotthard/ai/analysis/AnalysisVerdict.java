package ch.gotthard.ai.analysis;

import ch.gotthard.core.model.RiskLevel;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * The three things a model is asked for, and the only three it may return.
 *
 * <p>This record <em>is</em> the contract: {@code AnthropicLlmClient} hands the class to the SDK's
 * {@code outputConfig(Class)}, which derives a JSON schema from it and constrains generation with
 * {@code output_config.format}. Nothing anywhere parses prose out of a reply — a field added here
 * changes the schema the model is generating against, and a field removed stops being accepted. The
 * {@code @JsonPropertyDescription} annotations are not documentation for us; they travel into the
 * schema and are the model's instructions for each field.
 *
 * <p>{@link #assessedLevel} is the model's own call, kept apart from the rules' computed level all
 * the way into {@code ai_analyses.assessed_level}. It is asked for precisely so the two can
 * disagree.
 */
@JsonClassDescription("A compliance analyst's verdict on one customer's activity in one window.")
public record AnalysisVerdict(
        @JsonPropertyDescription("Your own independent read of the evidence, which may differ from the level the"
                        + " deterministic rules computed. One of LOW, MEDIUM, HIGH, CRITICAL.")
                RiskLevel assessedLevel,
        @JsonPropertyDescription("Three to five sentences an operator can read aloud on a call: the pattern, the"
                        + " channel, and the figures that matter. If your level differs from the computed"
                        + " one, say so and say why. No preamble.")
                String summary,
        @JsonPropertyDescription("Two to five concrete next actions, imperative, one line each, each a single thing a"
                        + " human can do today.")
                List<String> recommendations) {

    public AnalysisVerdict {
        recommendations = List.copyOf(recommendations);
    }
}
