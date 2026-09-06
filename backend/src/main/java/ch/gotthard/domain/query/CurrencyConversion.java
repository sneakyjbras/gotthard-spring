package ch.gotthard.domain.query;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * How the ledger's many currencies are folded into the one currency policy thresholds are written
 * in.
 *
 * <p>{@code transactions.amount} is denominated in {@code transactions.currency}, and that column
 * holds CHF next to EUR next to BTC. Summing them raw would produce a number that means nothing, and
 * every threshold the risk core compares against — 10 000 for structuring, 2 500 for quasi-cash — is
 * a figure in the customer's reporting currency. So every monetary signal is converted before it
 * leaves PostgreSQL.
 *
 * <p>The rates are data supplied by the caller, not a fact this package knows: the schema carries no
 * FX table, so the layer above decides what a rate is and this record only carries it into the
 * query. A currency with no rate converts one-for-one — exactly right for the reporting currency
 * itself, and honest rather than silently wrong for anything else.
 */
public record CurrencyConversion(String reportingCurrency, Map<String, BigDecimal> ratesToReportingCurrency) {

    /**
     * Separates codes and rates on their way into {@code string_to_array}. Two parallel text
     * parameters rather than two SQL arrays, because {@code NamedParameterJdbcTemplate} expands a
     * bound array into an {@code IN} list — which is not what a rate table wants.
     */
    static final String SEPARATOR = "|";

    public CurrencyConversion {
        reportingCurrency = Objects.requireNonNull(reportingCurrency, "reportingCurrency is required")
                .trim()
                .toUpperCase(Locale.ROOT);
        ratesToReportingCurrency = Map.copyOf(Objects.requireNonNull(ratesToReportingCurrency, "rates are required"));
    }

    /** No conversion at all — every amount is already in the reporting currency. */
    public static CurrencyConversion identity(final String reportingCurrency) {
        return new CurrencyConversion(reportingCurrency, Map.of());
    }

    /** Currency codes, in the same order as {@link #rates()}. Null when there is no rate table. */
    String currencyCodes() {
        return joined(Map.Entry::getKey);
    }

    /** Rates, in the same order as {@link #currencyCodes()}. Null when there is no rate table. */
    String rates() {
        return joined(rate -> rate.getValue().toPlainString());
    }

    /**
     * Null rather than an empty string when there is nothing to convert: {@code string_to_array('',
     * '|')} yields one empty element, which would become a rate row with no currency and no number,
     * whereas {@code string_to_array(NULL, '|')} yields no rows at all.
     */
    private String joined(final Function<Map.Entry<String, BigDecimal>, String> part) {
        return ratesToReportingCurrency.isEmpty()
                ? null
                : ratesToReportingCurrency.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(part)
                        .collect(Collectors.joining(SEPARATOR));
    }
}
