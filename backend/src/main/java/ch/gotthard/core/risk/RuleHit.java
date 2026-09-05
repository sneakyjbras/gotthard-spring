package ch.gotthard.core.risk;

import ch.gotthard.core.model.Money;
import ch.gotthard.core.model.Require;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A rule that fired, and what it added to the score.
 *
 * <p>This is a {@code risk_assessments} row before it has met the database: the rule code resolves
 * to {@code rule_id} above, the transaction it belongs to is the one that was scored. Every hit is
 * written; that table is the audit trail.
 */
public record RuleHit(String ruleCode, BigDecimal contribution) {

    public RuleHit {
        ruleCode = Require.code(ruleCode, "ruleCode");
        contribution = Require.notNegative(contribution, "contribution").setScale(Money.SCALE, RoundingMode.HALF_UP);
    }
}
