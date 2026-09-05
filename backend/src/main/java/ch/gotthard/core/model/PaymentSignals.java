package ch.gotthard.core.model;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * The corridors a customer's payments have been running down.
 *
 * <p>A corridor is a country pair, and the country that matters is the beneficiary bank's — {@code
 * payment_activity.receiver_bank_country}. Which countries are elevated risk is a policy question,
 * answered by the rule that asks it, not by this record.
 *
 * @param counterpartyCountries7d beneficiary bank countries seen in the last seven days, ISO-3166
 *     alpha-2, upper case
 * @param crossBorderCount7d payments in that window whose beneficiary bank sat outside the
 *     customer's own country
 * @param crossBorderVolume7d what those payments came to
 */
public record PaymentSignals(Set<String> counterpartyCountries7d, int crossBorderCount7d, Money crossBorderVolume7d) {

    private static final int COUNTRY_CODE_LENGTH = 2;

    public PaymentSignals {
        counterpartyCountries7d = normalisedCountries(counterpartyCountries7d);
        Require.notNegative(crossBorderCount7d, "crossBorderCount7d");
        Require.present(crossBorderVolume7d, "crossBorderVolume7d");
    }

    /** No payment activity — what a card or crypto transaction carries. */
    public static PaymentSignals none(final String currency) {
        return new PaymentSignals(Set.of(), 0, Money.zero(currency));
    }

    public boolean touches(final Set<String> countries) {
        return counterpartyCountries7d.stream().anyMatch(Require.present(countries, "countries")::contains);
    }

    private static Set<String> normalisedCountries(final Set<String> countries) {
        return Require.present(countries, "counterpartyCountries7d").stream()
                .map(PaymentSignals::normalisedCountry)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalisedCountry(final String country) {
        final String code = Require.code(country, "counterpartyCountry");
        if (code.length() != COUNTRY_CODE_LENGTH) {
            throw new IllegalArgumentException("country must be ISO-3166 alpha-2, was " + code);
        }
        return code;
    }
}
