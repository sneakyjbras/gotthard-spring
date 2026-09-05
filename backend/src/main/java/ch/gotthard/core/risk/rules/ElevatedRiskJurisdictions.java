package ch.gotthard.core.risk.rules;

import java.util.Set;

/**
 * The countries a payment corridor is treated as elevated risk for reaching.
 *
 * <p>Stands in for the FATF lists a compliance function actually tracks. Those move on a policy
 * cycle, so in a real deployment this belongs in reference data next to the rule weights; here it is
 * a constant, and the rule that reads it says where it came from.
 */
public final class ElevatedRiskJurisdictions {

    /** ISO-3166 alpha-2, upper case, matching {@code payment_activity.receiver_bank_country}. */
    public static final Set<String> CODES = Set.of("AF", "HT", "IR", "KP", "MM", "SY", "YE");

    private ElevatedRiskJurisdictions() {}
}
