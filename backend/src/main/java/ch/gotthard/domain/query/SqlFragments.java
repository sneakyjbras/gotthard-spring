package ch.gotthard.domain.query;

/**
 * SQL every query in this package shares.
 *
 * <p>Only two things are genuinely common — turning the bound rate parameters back into a table, and
 * applying that table to an amount — and both appear in more than one statement. Repeating them is
 * how two queries quietly stop agreeing about what a franc is.
 */
final class SqlFragments {

    /**
     * The bound rate parameters, unpacked into a two-column table.
     *
     * <p>{@code unnest} over two parallel arrays pairs each code with its rate positionally, which
     * is why {@link CurrencyConversion} builds both strings from one sorted pass. The explicit
     * {@code CAST(... AS text)} is not decoration: with no rate table both parameters bind as SQL
     * NULL, and PostgreSQL will not infer an argument type for {@code string_to_array} without it.
     */
    static final String RATES_CTE =
            """
            rates(currency, rate) AS (
                SELECT code, CAST(value AS numeric)
                FROM unnest(
                        string_to_array(CAST(:rateCurrencies AS text), '|'),
                        string_to_array(CAST(:rateValues AS text), '|')) AS pair(code, value)
            )""";

    /**
     * A transaction's amount in the reporting currency. An unrated currency converts one-for-one
     * rather than vanishing into a NULL that would poison every sum it touched.
     */
    static final String REPORTING_AMOUNT = "round(t.amount * COALESCE(r.rate, 1), 2)";

    private SqlFragments() {}
}
