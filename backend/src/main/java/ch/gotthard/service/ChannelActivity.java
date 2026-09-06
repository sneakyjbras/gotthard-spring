package ch.gotthard.service;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.Money;
import java.time.OffsetDateTime;

/**
 * What one channel did in the window. Volume is in the reporting currency, so the three channels'
 * figures can be compared and added.
 */
public record ChannelActivity(
        ActivityType channel,
        int transactionCount,
        Money volume,
        int unsuccessfulCount,
        OffsetDateTime firstAt,
        OffsetDateTime lastAt) {}
