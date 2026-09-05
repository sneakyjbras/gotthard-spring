package ch.gotthard.core.model;

import java.util.Set;

/**
 * The three channels a customer transacts through, mirroring the {@code activity_type} check
 * constraint and the three activity detail tables.
 */
public enum ActivityType {
    CARD,
    PAYMENT,
    CRYPTO;

    /**
     * Every channel — what a rule declares when it is channel-agnostic, and the Java side of {@code
     * risk_rules.applies_to = 'ALL'}.
     */
    public static final Set<ActivityType> ALL = Set.of(values());
}
