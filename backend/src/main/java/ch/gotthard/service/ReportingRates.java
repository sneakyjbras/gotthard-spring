package ch.gotthard.service;

import ch.gotthard.domain.query.CurrencyConversion;
import java.math.BigDecimal;
import java.util.Map;

/**
 * The currency every monetary signal is stated in, and what the others are worth in it.
 *
 * <p>The rules compare against figures a compliance policy wrote down — 10 000 for structuring,
 * 2 500 for quasi-cash concentration — and those figures are in one currency. The ledger is not: a
 * customer's week is CHF next to EUR next to BTC, and 0.2 BTC is not a small transfer however small
 * the number looks. Something has to convert, and this is it.
 *
 * <p><b>These rates are indicative and fixed.</b> The schema carries no rate table and inventing a
 * migration for one is outside this layer's remit, so they are stated here, in one place, where a
 * reader can see exactly what they are. A production deployment replaces this class with a rate feed
 * and nothing else changes — the conversion is already a parameter of every query that needs it.
 */
public final class ReportingRates {

    /** Swiss francs. The bank is in Zurich and the policy thresholds were written in francs. */
    public static final String REPORTING_CURRENCY = "CHF";

    private static final Map<String, BigDecimal> INDICATIVE_RATES = Map.of(
            "CHF", BigDecimal.ONE,
            "EUR", new BigDecimal("0.94"),
            "USD", new BigDecimal("0.88"),
            "GBP", new BigDecimal("1.12"),
            "USDT", new BigDecimal("0.88"),
            "USDC", new BigDecimal("0.88"),
            "BTC", new BigDecimal("55000"),
            "ETH", new BigDecimal("2800"));

    private ReportingRates() {}

    /** The conversion the queries apply. An unlisted currency is left at one-for-one. */
    public static CurrencyConversion conversion() {
        return new CurrencyConversion(REPORTING_CURRENCY, INDICATIVE_RATES);
    }
}
