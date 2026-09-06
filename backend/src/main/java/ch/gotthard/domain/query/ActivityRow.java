package ch.gotthard.domain.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The transaction a feature row was computed for, with its amount already converted into the
 * reporting currency.
 *
 * <p>{@code activityType} is the raw {@code transactions.activity_type} string rather than an enum:
 * this package reads columns, and turning a column into a type the risk core recognises is the
 * assembler's job.
 */
public record ActivityRow(
        UUID transactionId,
        UUID customerId,
        String activityType,
        OffsetDateTime occurredAt,
        BigDecimal amount,
        String reportingCurrency) {}
