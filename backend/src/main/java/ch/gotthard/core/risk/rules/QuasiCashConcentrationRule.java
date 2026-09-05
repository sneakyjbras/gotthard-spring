package ch.gotthard.core.risk.rules;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.risk.Rule;
import java.math.BigDecimal;
import java.util.Set;

/**
 * R-07 — quasi-cash concentration. A day's card spend of 2 500 or more at quasi-cash, money-transfer
 * and gambling merchants.
 *
 * <p>Those merchant categories are the ones where a card comes closest to being a cash machine, and
 * concentration in them over a single day is the classic card-side cash-out. Which merchant
 * categories count is decided in the query that sums the spend; this rule only asks how much of it
 * there was.
 */
public final class QuasiCashConcentrationRule implements Rule {

    public static final String CODE = "R-07";

    /** In the customer's reporting currency, over the twenty-four hours ending at this activity. */
    public static final BigDecimal MIN_QUASI_CASH_VOLUME = new BigDecimal("2500.00");

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<ActivityType> appliesTo() {
        return Set.of(ActivityType.CARD);
    }

    @Override
    public boolean fires(final Features features) {
        return features.card().quasiCashVolume24h().hasAmountAtLeast(MIN_QUASI_CASH_VOLUME);
    }
}
