package ch.gotthard.domain.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One transaction as an operator reads it, in the currency it actually happened in.
 *
 * <p>Unlike everything the window query produces, this is not converted: a list of a customer's
 * transactions should show what the customer was charged, not a reporting-currency restatement of
 * it. Conversion belongs to the aggregates that get compared against policy figures.
 *
 * @param counterparty whoever was on the other side — beneficiary account, receiving wallet, or
 *     merchant, depending on the channel
 * @param channelDetail the one field that identifies the channel's flavour: merchant category,
 *     beneficiary bank country, or blockchain
 */
public record RecentActivityRow(
        UUID transactionId,
        String activityType,
        BigDecimal amount,
        String currency,
        String status,
        OffsetDateTime occurredAt,
        String counterparty,
        String channelDetail) {}
