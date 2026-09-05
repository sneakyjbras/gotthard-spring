package ch.gotthard.core.model;

import java.time.Instant;
import java.util.UUID;

/**
 * The transaction being scored, stripped to what a rule may look at: who, which channel, how much,
 * and when. Everything else about it is either irrelevant to risk or arrives as a precomputed signal
 * on {@link Features}.
 */
public record Activity(UUID transactionId, UUID customerId, ActivityType type, Money amount, Instant occurredAt) {

    public Activity {
        Require.present(transactionId, "transactionId");
        Require.present(customerId, "customerId");
        Require.present(type, "type");
        Require.present(amount, "amount");
        Require.present(occurredAt, "occurredAt");
    }
}
