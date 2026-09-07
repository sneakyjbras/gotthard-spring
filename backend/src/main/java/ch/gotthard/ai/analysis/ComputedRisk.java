package ch.gotthard.ai.analysis;

import ch.gotthard.core.model.RiskLevel;
import java.math.BigDecimal;

/**
 * What the deterministic rules decided, handed to the model as a fact rather than as a question.
 *
 * <p><b>The AI never decides risk.</b> This record is in the prompt so that the write-up explains a
 * score which already exists; the model's own assessed level is recorded beside it precisely so the
 * two can be compared, never so that one can overwrite the other.
 */
public record ComputedRisk(BigDecimal score, RiskLevel level, int transactionsEvaluated) {}
