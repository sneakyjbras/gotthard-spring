package ch.gotthard.domain.query;

import java.math.BigDecimal;

/**
 * The channel-agnostic window aggregates: what moved, how often, and how long the account had been
 * quiet first.
 *
 * @param rollingVolume24h summed reporting amount over the twenty-four hours ending at this activity
 * @param rollingVolume7d summed reporting amount over the seven days ending at this activity
 * @param transactionCount24h activities in the same twenty-four hours, this one included
 * @param nearThresholdCount7d activities to the same beneficiary in seven days whose amount sat just
 *     below the reporting threshold
 * @param dormancySeconds gap to the customer's previous activity, nought when there was none
 */
public record VelocityRow(
        BigDecimal rollingVolume24h,
        BigDecimal rollingVolume7d,
        long transactionCount24h,
        long nearThresholdCount7d,
        long dormancySeconds) {}
