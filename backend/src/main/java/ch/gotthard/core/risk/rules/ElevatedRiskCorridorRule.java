package ch.gotthard.core.risk.rules;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.model.PaymentSignals;
import ch.gotthard.core.risk.Rule;
import java.math.BigDecimal;
import java.util.Set;

/**
 * R-02 — cross-border corridor. Payments to a beneficiary bank in an elevated-risk jurisdiction,
 * either repeated within seven days or adding up to 5 000 or more.
 *
 * <p>A single transfer to a listed country is a fact about a customer's life. A corridor — the same
 * destination, again, or at size — is a fact about their money.
 */
public final class ElevatedRiskCorridorRule implements Rule {

    public static final String CODE = "R-02";

    /** One transfer is a transfer. Two down the same corridor is a corridor. */
    public static final int MIN_CROSS_BORDER_TRANSFERS = 2;

    /** Or a single week's worth at this size, however few the transfers. */
    public static final BigDecimal MIN_CORRIDOR_VOLUME = new BigDecimal("5000.00");

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<ActivityType> appliesTo() {
        return Set.of(ActivityType.PAYMENT);
    }

    @Override
    public boolean fires(final Features features) {
        final PaymentSignals payment = features.payment();
        return payment.touches(ElevatedRiskJurisdictions.CODES) && (isRepeated(payment) || isSubstantial(payment));
    }

    private static boolean isRepeated(final PaymentSignals payment) {
        return payment.crossBorderCount7d() >= MIN_CROSS_BORDER_TRANSFERS;
    }

    private static boolean isSubstantial(final PaymentSignals payment) {
        return payment.crossBorderVolume7d().hasAmountAtLeast(MIN_CORRIDOR_VOLUME);
    }
}
