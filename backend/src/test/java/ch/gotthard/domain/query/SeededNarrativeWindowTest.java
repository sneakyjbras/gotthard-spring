package ch.gotthard.domain.query;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.service.ReportingRates;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The window functions against the dataset the demo actually runs on.
 *
 * <p>Everything else in this package builds its own fixture. This one does not: it reads {@code
 * V4__seed_demo_data.sql} — Sandra Wyss's five near-threshold transfers to one Myanmar account — and
 * asserts the numbers worked out by hand from the migration's own timestamps. A query that is
 * correct on a fixture written to suit it and wrong on the demo data would still pass every other
 * test in this suite; this is the one that would not.
 *
 * <p>The migration states each timestamp as {@code now() - interval '<n> seconds'}, so the five
 * transfers sit at these distances behind whatever instant the migration was applied at:
 *
 * <pre>
 *   622 080 s = 7.2 days    9 450
 *   492 480 s = 5.7 days    9 680
 *   354 240 s = 4.1 days    9 300
 *   224 640 s = 2.6 days    9 750
 *    86 400 s = 1.0 day     9 540
 * </pre>
 *
 * <p>All five go to account {@code 008812345671} at a bank in MM, and her nearest card purchase is
 * 775 844 seconds (8.98 days) back — which is what makes the seven-day frames below come out to
 * round figures.
 */
class SeededNarrativeWindowTest extends AbstractQueryTest {

    private static final UUID SANDRA_WYSS = UUID.fromString("14781492-5cc9-5660-a44e-8330e87e7736");

    /** The five transfers, oldest first. Ids are stable: the migration writes them literally. */
    private static final UUID AT_7_2_DAYS = UUID.fromString("5106432d-4620-52d9-935b-50091a755d94");

    private static final UUID AT_5_7_DAYS = UUID.fromString("b81cc3fe-d932-53b0-a73f-96d3b4620fce");
    private static final UUID AT_4_1_DAYS = UUID.fromString("a26b3bfc-e930-5173-9ee4-351f4466cfc6");
    private static final UUID AT_2_6_DAYS = UUID.fromString("22244955-f3a2-53bf-b668-e3f1938ebfd0");
    private static final UUID AT_1_0_DAYS = UUID.fromString("461166a2-c37d-54e2-b794-2d81aaf180d7");

    /** The band the reporting threshold implies: 9 000 up to 10 000. All five amounts sit inside it. */
    private static final NearThresholdBand BAND = NearThresholdBand.below(new BigDecimal("10000.00"));

    private FeatureWindowQuery query;

    @BeforeEach
    void createQuery() {
        query = new FeatureWindowQuery(jdbc);
    }

    /**
     * Each transfer's seven days reach back over the ones before it, and every one of the five is in
     * the band and went to the same account — so the counts climb 1, 2, 3, 4, 5. Structuring is
     * exactly this shape, and this is the query that has to see it.
     */
    @Test
    void given_theSeededStructuringNarrative_when_findFeatures_then_theNearThresholdCountClimbsToFive() {
        final List<FeatureRow> rows = features();

        assertThat(nearThreshold(rows, AT_7_2_DAYS)).isEqualTo(1);
        assertThat(nearThreshold(rows, AT_5_7_DAYS)).isEqualTo(2);
        assertThat(nearThreshold(rows, AT_4_1_DAYS)).isEqualTo(3);
        assertThat(nearThreshold(rows, AT_2_6_DAYS)).isEqualTo(4);
        assertThat(nearThreshold(rows, AT_1_0_DAYS)).isEqualTo(5);
    }

    /**
     * Running totals of the same five: 9 450, then 19 130, 28 430, 38 180 and 47 720. Her other
     * payments are all Swiss and all more than a fortnight older, so nothing else is in the frame.
     */
    @Test
    void given_theSeededStructuringNarrative_when_findFeatures_then_theCorridorVolumeAccumulates() {
        final List<FeatureRow> rows = features();

        assertCorridor(rows, AT_7_2_DAYS, 1, "9450.00");
        assertCorridor(rows, AT_5_7_DAYS, 2, "19130.00");
        assertCorridor(rows, AT_4_1_DAYS, 3, "28430.00");
        assertCorridor(rows, AT_2_6_DAYS, 4, "38180.00");
        assertCorridor(rows, AT_1_0_DAYS, 5, "47720.00");
    }

    @Test
    void given_theSeededStructuringNarrative_when_findFeatures_then_myanmarIsTheOnlyCorridor() {
        assertThat(row(features(), AT_1_0_DAYS).payment().counterpartyCountries7d())
                .containsOnly("MM")
                .hasSize(5);
    }

    /**
     * The seven-day sum is customer-wide, not per beneficiary — and at the fourth transfer it picks up
     * a 39.54 card purchase 8.98 days back, giving 38 219.54 where the corridor volume was 38 180.
     * The difference is the point: the corridor figure is partitioned, the velocity figure is not, and
     * a query that confused the two would produce the same number twice.
     */
    @Test
    void given_theSeededStructuringNarrative_when_findFeatures_then_theWeeksVelocityIncludesCardSpend() {
        final List<FeatureRow> rows = features();

        assertThat(row(rows, AT_2_6_DAYS).velocity().rollingVolume7d()).isEqualByComparingTo("38219.54");
        assertThat(row(rows, AT_2_6_DAYS).payment().crossBorderVolume7d()).isEqualByComparingTo("38180.00");
    }

    /**
     * Her nearest card purchase is 8.98 days back, so the newest transfer's week holds only the other
     * four transfers: 47 720. Its day holds nothing but itself, and the gap to the transfer before it
     * is 224 640 − 86 400 = 138 240 seconds.
     */
    @Test
    void given_theSeededStructuringNarrative_when_findFeatures_then_theNewestTransfersWindowsAreExact() {
        final FeatureRow newest = row(features(), AT_1_0_DAYS);

        assertThat(newest.velocity().rollingVolume7d()).isEqualByComparingTo("47720.00");
        assertThat(newest.velocity().rollingVolume24h()).isEqualByComparingTo("9540.00");
        assertThat(newest.velocity().transactionCount24h()).isEqualTo(1);
        assertThat(newest.velocity().dormancySeconds()).isEqualTo(138_240L);
    }

    private List<FeatureRow> features() {
        final OffsetDateTime now = OffsetDateTime.now();
        return query.findFeatures(SANDRA_WYSS, now.minusDays(30), now, ReportingRates.conversion(), BAND);
    }

    private static void assertCorridor(
            final List<FeatureRow> rows, final UUID transactionId, final long count, final String volume) {
        final PaymentRow payment = row(rows, transactionId).payment();
        assertThat(payment.crossBorderCount7d())
                .as("cross-border count at %s", transactionId)
                .isEqualTo(count);
        assertThat(payment.crossBorderVolume7d())
                .as("cross-border volume at %s", transactionId)
                .isEqualByComparingTo(volume);
    }

    private static long nearThreshold(final List<FeatureRow> rows, final UUID transactionId) {
        return row(rows, transactionId).velocity().nearThresholdCount7d();
    }

    private static FeatureRow row(final List<FeatureRow> rows, final UUID transactionId) {
        return rows.stream()
                .filter(row -> row.activity().transactionId().equals(transactionId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no feature row for " + transactionId));
    }
}
