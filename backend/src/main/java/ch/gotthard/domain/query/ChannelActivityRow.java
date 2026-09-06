package ch.gotthard.domain.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One channel's slice of a customer's activity in a window.
 *
 * @param activityType {@code CARD}, {@code PAYMENT} or {@code CRYPTO}
 * @param transactionCount activities on that channel in the window
 * @param volume what they came to, in the reporting currency
 * @param failedCount how many did not complete
 * @param firstAt earliest activity on the channel in the window
 * @param lastAt latest activity on the channel in the window
 */
public record ChannelActivityRow(
        String activityType,
        long transactionCount,
        BigDecimal volume,
        long failedCount,
        OffsetDateTime firstAt,
        OffsetDateTime lastAt) {}
