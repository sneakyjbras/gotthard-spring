package ch.gotthard.service;

import ch.gotthard.core.model.Money;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The screen an operator opens after finding a customer: how much moved, through which channels, and
 * the last few things that happened.
 *
 * <p>The totals are sums of the channel breakdown rather than a fourth query — three numbers added
 * in Java cost less than a {@code GROUPING SETS} costs to read.
 *
 * @param channels only the channels the customer actually used; a silent channel is absent, not
 *     present with noughts in it
 */
public record ActivityOverview(
        CustomerView customer,
        OffsetDateTime from,
        OffsetDateTime to,
        int transactionCount,
        Money totalVolume,
        int unsuccessfulCount,
        List<ChannelActivity> channels,
        List<TransactionView> recentTransactions) {

    public ActivityOverview {
        channels = List.copyOf(channels);
        recentTransactions = List.copyOf(recentTransactions);
    }
}
