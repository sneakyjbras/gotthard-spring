package ch.gotthard.domain.query;

import java.math.BigDecimal;

/**
 * Card window aggregates. Every component is nought on a payment or crypto row.
 *
 * @param declineCount1h declined card authorisations in the hour ending at this activity
 * @param cardNotPresentDeclineCount1h how many of those were card-not-present
 * @param distinctMerchantCount1h merchants touched in the same hour, declines included
 * @param quasiCashVolume24h day's spend at quasi-cash, money-transfer and gambling merchants
 */
public record CardRow(
        long declineCount1h,
        long cardNotPresentDeclineCount1h,
        long distinctMerchantCount1h,
        BigDecimal quasiCashVolume24h) {}
