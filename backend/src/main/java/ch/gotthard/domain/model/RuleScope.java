package ch.gotthard.domain.model;

/**
 * Which activity a {@link RiskRule} evaluates. Mirrors the {@code CHECK (applies_to IN
 * ('CARD','PAYMENT','CRYPTO','ALL'))} constraint on {@code risk_rules.applies_to}.
 *
 * <p>Deliberately not the same type as the {@link Transaction} discriminator: a rule can also apply
 * to {@code ALL} activity types, a value the discriminator itself never takes.
 */
public enum RuleScope {
    CARD,
    PAYMENT,
    CRYPTO,
    ALL
}
