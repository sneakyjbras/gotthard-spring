package ch.gotthard.core.risk.rules;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.model.CardSignals;
import ch.gotthard.core.model.Features;
import ch.gotthard.core.risk.Rule;
import java.util.Set;

/**
 * R-03 — card testing. Three or more card-not-present declines within an hour, spread across two or
 * more merchants.
 *
 * <p>This is what a stolen card number looks like while it is being validated: small
 * card-not-present attempts, fired at whatever merchants will take them, most of them refused. The
 * spread across merchants is what separates it from a customer mistyping an expiry date.
 */
public final class CardNotPresentDeclineClusterRule implements Rule {

    public static final String CODE = "R-03";

    /** Below this a decline run is a customer having a bad afternoon. */
    public static final int MIN_CARD_NOT_PRESENT_DECLINES = 3;

    /** Declines at a single merchant are that merchant's problem, not the card's. */
    public static final int MIN_DISTINCT_MERCHANTS = 2;

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
        final CardSignals card = features.card();
        return card.cardNotPresentDeclineCount1h() >= MIN_CARD_NOT_PRESENT_DECLINES
                && card.distinctMerchantCount1h() >= MIN_DISTINCT_MERCHANTS;
    }
}
