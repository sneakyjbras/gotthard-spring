package ch.gotthard.service;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.Money;
import ch.gotthard.domain.query.ActivityOverviewQuery;
import ch.gotthard.domain.query.ChannelActivityRow;
import ch.gotthard.domain.query.RecentActivityRow;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A customer's activity in a window: what moved, through which channels, and the last few
 * transactions.
 *
 * <p>PostgreSQL does the set-based work — one grouped aggregate and one ordered page — and this
 * class does the deciding: which currency the figures are stated in, how many recent transactions
 * are worth showing, and what the totals are. Nothing here iterates a result set to build a sum that
 * the database could have produced.
 */
@Service
public class ActivityOverviewService {

    /** Enough to see the shape of the last few days without becoming a statement. */
    public static final int RECENT_TRANSACTION_LIMIT = 25;

    private final CustomerSearchService customers;
    private final ActivityOverviewQuery query;

    public ActivityOverviewService(final CustomerSearchService customers, final ActivityOverviewQuery query) {
        this.customers = customers;
        this.query = query;
    }

    @Transactional(readOnly = true)
    public ActivityOverview overview(final UUID customerId, final ActivityWindow window) {
        final CustomerView customer = customers.findById(customerId);
        final List<ChannelActivity> channels = channels(customerId, window);
        return new ActivityOverview(
                customer,
                window.from(),
                window.to(),
                sum(channels, ChannelActivity::transactionCount),
                totalVolume(channels),
                sum(channels, ChannelActivity::unsuccessfulCount),
                channels,
                recentTransactions(customerId, window));
    }

    private List<ChannelActivity> channels(final UUID customerId, final ActivityWindow window) {
        return query.findChannelBreakdown(customerId, window.from(), window.to(), ReportingRates.conversion()).stream()
                .map(ActivityOverviewService::channelOf)
                .toList();
    }

    private List<TransactionView> recentTransactions(final UUID customerId, final ActivityWindow window) {
        return query.findRecentActivity(customerId, window.from(), window.to(), RECENT_TRANSACTION_LIMIT).stream()
                .map(ActivityOverviewService::transactionOf)
                .toList();
    }

    private static ChannelActivity channelOf(final ChannelActivityRow row) {
        return new ChannelActivity(
                ActivityType.valueOf(row.activityType()),
                Math.toIntExact(row.transactionCount()),
                Money.of(ReportingRates.REPORTING_CURRENCY, row.volume()),
                Math.toIntExact(row.failedCount()),
                row.firstAt(),
                row.lastAt());
    }

    private static TransactionView transactionOf(final RecentActivityRow row) {
        return new TransactionView(
                row.transactionId(),
                ActivityType.valueOf(row.activityType()),
                Money.of(row.currency(), row.amount()),
                row.status(),
                row.occurredAt(),
                row.counterparty(),
                trimmed(row.channelDetail()));
    }

    /** The three channels' volumes are already in one currency, so they simply add up. */
    private static Money totalVolume(final List<ChannelActivity> channels) {
        return channels.stream()
                .map(ChannelActivity::volume)
                .reduce(Money.zero(ReportingRates.REPORTING_CURRENCY), Money::plus);
    }

    private static int sum(final List<ChannelActivity> channels, final ToIntFunction<ChannelActivity> part) {
        return channels.stream().mapToInt(part).sum();
    }

    /** {@code CHAR(4)} merchant category codes arrive blank-padded; nothing downstream wants that. */
    private static String trimmed(final String detail) {
        return detail == null ? null : detail.trim();
    }
}
