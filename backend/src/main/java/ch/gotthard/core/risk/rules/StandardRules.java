package ch.gotthard.core.risk.rules;

import ch.gotthard.core.risk.Rule;
import java.util.List;

/**
 * The rules this system ships with, ready to hand to a {@link ch.gotthard.core.risk.RiskScorer}.
 *
 * <p>A convenience for wiring, not a registry the scorer consults. Every code here needs a {@code
 * risk_rules} row carrying its weight, and a rule with no enabled row is inert — which is how a rule
 * is switched off. Adding a rule means a class, a row and a line here, and still never an edit to
 * the scorer.
 */
public final class StandardRules {

    private StandardRules() {}

    public static List<Rule> all() {
        return List.of(
                new NearThresholdStructuringRule(),
                new ElevatedRiskCorridorRule(),
                new CardNotPresentDeclineClusterRule(),
                new RapidExchangeDisposalRule(),
                new FlaggedWalletProximityRule(),
                new DormancyBurstRule(),
                new QuasiCashConcentrationRule());
    }
}
