package ch.gotthard.core.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An amount of money in one currency.
 *
 * <p>Amounts are normalised to the scale of {@code transactions.amount}, so two sums that are equal
 * arithmetically are equal as records too — {@code 10} and {@code 10.00} would not be otherwise.
 *
 * <p>The currency is free text rather than {@link java.util.Currency} because the ledger also
 * carries crypto assets, and {@code BTC} is not ISO-4217.
 */
public record Money(String currency, BigDecimal amount) {

    /** Two decimal places, matching {@code DECIMAL(18,2)} in the schema. */
    public static final int SCALE = 2;

    public Money {
        currency = Require.code(currency, "currency");
        amount = Require.present(amount, "amount").setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(final String currency, final BigDecimal amount) {
        return new Money(currency, amount);
    }

    /** Literal amounts, for policy figures and tests: {@code Money.of("CHF", "9500.00")}. */
    public static Money of(final String currency, final String amount) {
        return new Money(currency, new BigDecimal(Require.text(amount, "amount")));
    }

    public static Money zero(final String currency) {
        return new Money(currency, BigDecimal.ZERO);
    }

    public Money plus(final Money other) {
        return new Money(currency, amount.add(sameCurrencyAs(other).amount));
    }

    public boolean isAtLeast(final Money other) {
        return amount.compareTo(sameCurrencyAs(other).amount) >= 0;
    }

    /**
     * Compares the amount against a policy figure, ignoring the currency.
     *
     * <p>Rules hold their thresholds as plain numbers because a threshold written into a compliance
     * policy — "aggregate transfers of 10 000 or more" — is stated in the customer's reporting
     * currency, which the feature query has already converted every monetary signal into. The
     * currency-aware comparison is {@link #isAtLeast(Money)}; this one is deliberately blind.
     */
    public boolean hasAmountAtLeast(final BigDecimal minimum) {
        return amount.compareTo(Require.present(minimum, "minimum")) >= 0;
    }

    private Money sameCurrencyAs(final Money other) {
        if (!currency.equals(Require.present(other, "other").currency)) {
            throw new IllegalArgumentException("currency mismatch: " + currency + " and " + other.currency);
        }
        return other;
    }
}
