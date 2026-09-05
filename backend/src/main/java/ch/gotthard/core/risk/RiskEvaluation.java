package ch.gotthard.core.risk;

import ch.gotthard.core.model.Require;
import ch.gotthard.core.model.RiskScore;
import java.util.List;

/**
 * What the rules made of one transaction: the score, the band it falls into, and every rule that
 * fired to get there.
 *
 * <p>The hits are not a debugging aid. They are the audit trail that has to be written, and the list
 * of signals the retrieval layer turns into the policy clauses the model is shown.
 */
public record RiskEvaluation(RiskScore score, List<RuleHit> hits) {

    public RiskEvaluation {
        Require.present(score, "score");
        hits = List.copyOf(Require.present(hits, "hits"));
    }

    /** Nothing fired: a clean transaction, scored and recorded as such. */
    public static RiskEvaluation clean() {
        return new RiskEvaluation(RiskScore.zero(), List.of());
    }

    public List<String> firedRuleCodes() {
        return hits.stream().map(RuleHit::ruleCode).toList();
    }
}
