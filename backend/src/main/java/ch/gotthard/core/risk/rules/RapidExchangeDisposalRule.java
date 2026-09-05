package ch.gotthard.core.risk.rules;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.CryptoSignals;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.risk.Rule;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

/**
 * R-04 — rapid disposal. Crypto sent on to an exchange within an hour of arriving in the wallet,
 * with 10 000 or more leaving that wallet over the day.
 *
 * <p>An exchange is where crypto turns back into money, and the hour is the tell: an investor holds,
 * a layer moves. Neither half is remarkable alone — wallets receive funds, and wallets pay
 * exchanges — so both are required, and the day's outflow keeps the rule off small transfers.
 */
public final class RapidExchangeDisposalRule implements Rule {

    public static final String CODE = "R-04";

    /** Held for less than this before being sent on, the transfer is a pass-through. */
    public static final Duration MAX_TIME_TO_DISPOSAL = Duration.ofHours(1);

    /** In the customer's reporting currency, so a fast small transfer stays unremarkable. */
    public static final BigDecimal MIN_OUTBOUND_VOLUME = new BigDecimal("10000.00");

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<ActivityType> appliesTo() {
        return Set.of(ActivityType.CRYPTO);
    }

    @Override
    public boolean fires(final Features features) {
        final CryptoSignals crypto = features.crypto();
        return crypto.destinationIsExchange()
                && crypto.movedWithin(MAX_TIME_TO_DISPOSAL)
                && crypto.outboundVolume24h().hasAmountAtLeast(MIN_OUTBOUND_VOLUME);
    }
}
