package ch.gotthard.core.model;

/**
 * What card activity looks like around this transaction.
 *
 * <p>The merchant category never reaches the core as a code: classifying an MCC as quasi-cash or
 * gambling is a lookup, and lookups belong in the query. What arrives is the money that landed in
 * those categories.
 *
 * @param declineCount1h declined card authorisations in the hour ending at this activity
 * @param cardNotPresentDeclineCount1h how many of those were card-not-present
 * @param distinctMerchantCount1h merchants touched in the same hour, declines included
 * @param quasiCashVolume24h day's spend at quasi-cash, money-transfer and gambling merchants
 */
public record CardSignals(
        int declineCount1h, int cardNotPresentDeclineCount1h, int distinctMerchantCount1h, Money quasiCashVolume24h) {

    public CardSignals {
        Require.notNegative(declineCount1h, "declineCount1h");
        Require.notNegative(cardNotPresentDeclineCount1h, "cardNotPresentDeclineCount1h");
        Require.notNegative(distinctMerchantCount1h, "distinctMerchantCount1h");
        Require.present(quasiCashVolume24h, "quasiCashVolume24h");
        requireDeclineBreakdownAddsUp(declineCount1h, cardNotPresentDeclineCount1h);
    }

    /** No card activity — what a payment or crypto transaction carries. */
    public static CardSignals none(final String currency) {
        return new CardSignals(0, 0, 0, Money.zero(currency));
    }

    private static void requireDeclineBreakdownAddsUp(final int declines, final int cardNotPresent) {
        if (cardNotPresent > declines) {
            throw new IllegalArgumentException(
                    "card-not-present declines (" + cardNotPresent + ") exceed all declines (" + declines + ")");
        }
    }
}
