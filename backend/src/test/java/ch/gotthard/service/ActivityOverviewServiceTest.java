package ch.gotthard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.gotthard.core.model.ActivityType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The activity overview, totals included.
 *
 * <p>The totals are the channel figures added up, and the channel figures were worked out by hand in
 * {@code ActivityOverviewQueryTest}: 50 on cards, 1 440 on payments, 5 500 on crypto. Five
 * transactions, 6 990 francs, one of them unsuccessful.
 */
class ActivityOverviewServiceTest extends AbstractServiceIntegrationTest {

    private static final OffsetDateTime ANCHOR = OffsetDateTime.parse("2026-03-15T12:00:00Z");

    @Autowired
    private ActivityOverviewService activityOverview;

    @Test
    void given_activityOnEveryChannel_when_overview_then_theTotalsAreTheChannelsAddedUp() {
        final UUID customer = seedThreeChannels();

        final ActivityOverview overview = activityOverview.overview(customer, window());

        assertThat(overview.transactionCount()).isEqualTo(5);
        assertThat(overview.totalVolume().amount()).isEqualByComparingTo("6990.00");
        assertThat(overview.totalVolume().currency()).isEqualTo("CHF");
        assertThat(overview.unsuccessfulCount()).isEqualTo(1);
    }

    @Test
    void given_activityOnEveryChannel_when_overview_then_eachChannelIsBrokenOutSeparately() {
        final UUID customer = seedThreeChannels();

        final ActivityOverview overview = activityOverview.overview(customer, window());

        assertThat(overview.channels())
                .extracting(ChannelActivity::channel)
                .containsExactly(ActivityType.CARD, ActivityType.CRYPTO, ActivityType.PAYMENT);
        assertThat(overview.channels())
                .filteredOn(channel -> channel.channel() == ActivityType.CRYPTO)
                .singleElement()
                .satisfies(crypto -> assertThat(crypto.volume().amount()).isEqualByComparingTo("5500.00"));
    }

    /** Newest first, in the currency the customer was actually charged. */
    @Test
    void given_activityOnEveryChannel_when_overview_then_theRecentTransactionsCarryTheirOwnCurrency() {
        final UUID customer = seedThreeChannels();

        final ActivityOverview overview = activityOverview.overview(customer, window());

        assertThat(overview.recentTransactions()).hasSize(5);
        assertThat(overview.recentTransactions().getFirst().channel()).isEqualTo(ActivityType.CARD);
        assertThat(overview.recentTransactions())
                .filteredOn(transaction -> transaction.channel() == ActivityType.CRYPTO)
                .singleElement()
                .satisfies(crypto -> {
                    assertThat(crypto.amount().currency()).isEqualTo("BTC");
                    assertThat(crypto.amount().amount()).isEqualByComparingTo("0.10");
                    assertThat(crypto.channelDetail()).isEqualTo("BITCOIN");
                });
    }

    @Test
    void given_aCustomerWithNoActivity_when_overview_then_theTotalsAreNoughtRatherThanMissing() {
        final ActivityOverview overview = activityOverview.overview(fixtures.customer("CH"), window());

        assertThat(overview.transactionCount()).isZero();
        assertThat(overview.totalVolume().amount()).isEqualByComparingTo("0.00");
        assertThat(overview.channels()).isEmpty();
        assertThat(overview.recentTransactions()).isEmpty();
    }

    @Test
    void given_anUnknownCustomer_when_overview_then_itIsReportedAsMissing() {
        assertThatThrownBy(() -> activityOverview.overview(UUID.randomUUID(), window()))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    private UUID seedThreeChannels() {
        final UUID customer = fixtures.customer("CH");
        fixtures.card(customer, ANCHOR.minusDays(5), "20.00", "Alpha", "5411", true, null);
        fixtures.card(customer, ANCHOR.minusDays(1), "30.00", "Beta", "5411", true, null);
        fixtures.payment(customer, ANCHOR.minusDays(2), "1000.00", "EUR", "ACC-E", "DE");
        fixtures.paymentWithStatus(customer, ANCHOR.minusDays(3), "500.00", "FAILED");
        fixtures.crypto(customer, ANCHOR.minusDays(4), "0.10", "BTC", "aos-W1", "aos-W2", null);
        return customer;
    }

    private static ActivityWindow window() {
        return ActivityWindow.between(Optional.of(ANCHOR.minusDays(30)), Optional.of(ANCHOR));
    }
}
