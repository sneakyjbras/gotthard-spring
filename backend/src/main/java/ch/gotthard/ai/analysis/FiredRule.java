package ch.gotthard.ai.analysis;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One rule that fired, collapsed across every transaction it fired on.
 *
 * <p>{@link #thresholdLogic} is the {@code risk_rules.threshold_logic} column verbatim — the
 * human-readable statement of the condition, written for a person. It is in the prompt because
 * without it the model is shown a bare code like {@code R-01} and asked to explain something nobody
 * told it; with it, the model can say <em>why</em> the rule objected, in the same words the
 * compliance policy uses.
 *
 * @param occurrences how many transactions in the window tripped this rule
 * @param totalContribution what those firings contributed to the score, added up
 */
public record FiredRule(
        String ruleCode,
        String ruleName,
        String thresholdLogic,
        int occurrences,
        BigDecimal totalContribution,
        OffsetDateTime lastFiredAt) {}
