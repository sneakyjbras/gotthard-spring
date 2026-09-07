package ch.gotthard.service;

import ch.gotthard.core.model.ActivityType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One rule firing on one transaction — a {@code risk_assessments} row as an operator reads it, with
 * the rule's name and the transaction it was about filled in.
 */
public record RiskFinding(
        UUID transactionId,
        OffsetDateTime occurredAt,
        ActivityType channel,
        String ruleCode,
        String ruleName,
        String condition,
        BigDecimal contribution) {}
