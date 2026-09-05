package ch.gotthard.domain.model;

/**
 * Mirrors the {@code CHECK (... IN ('LOW','MEDIUM','HIGH','CRITICAL'))} constraint shared by {@code
 * ai_analyses.computed_level} and {@code ai_analyses.assessed_level}.
 *
 * <p>One type, two columns: the rules-computed level and the model's own assessed level are kept
 * apart on purpose (see {@link AiAnalysis}), but they are drawn from the same scale.
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
