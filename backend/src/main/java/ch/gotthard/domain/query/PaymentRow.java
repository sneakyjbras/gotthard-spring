package ch.gotthard.domain.query;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payment corridor aggregates. Empty and nought on a card or crypto row.
 *
 * <p>{@code counterpartyCountries7d} arrives as the raw {@code array_agg} of the window, duplicates
 * included — deduplicating is free where it lands, in the {@code Set} the risk core holds.
 *
 * @param counterpartyCountries7d beneficiary bank countries seen in the last seven days
 * @param crossBorderCount7d payments in that window whose beneficiary bank sat outside the
 *     customer's own country
 * @param crossBorderVolume7d what those payments came to, in the reporting currency
 */
public record PaymentRow(
        List<String> counterpartyCountries7d, long crossBorderCount7d, BigDecimal crossBorderVolume7d) {

    public PaymentRow {
        counterpartyCountries7d = List.copyOf(counterpartyCountries7d);
    }
}
