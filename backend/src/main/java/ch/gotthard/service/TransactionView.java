package ch.gotthard.service;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.Money;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One transaction on the activity screen, in the currency it actually happened in.
 *
 * @param counterparty the other side — beneficiary account, receiving wallet, or merchant
 * @param channelDetail what identifies the channel's flavour: merchant category code, beneficiary
 *     bank country, or blockchain
 */
public record TransactionView(
        UUID transactionId,
        ActivityType channel,
        Money amount,
        String status,
        OffsetDateTime occurredAt,
        String counterparty,
        String channelDetail) {}
