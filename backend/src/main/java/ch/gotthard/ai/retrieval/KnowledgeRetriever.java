package ch.gotthard.ai.retrieval;

import java.util.Collection;
import java.util.List;

/**
 * Retrieves policy text grounded in what the deterministic risk engine actually found — never in
 * operator free text. The caller hands over the codes of the rules that fired (e.g. {@code "R-01"});
 * this port hands back the policy chunks a compliance analyst would reach for to explain that finding,
 * ranked by similarity.
 *
 * <p>Free text never enters this contract, on purpose. The model does not get to go looking for
 * whatever policy language best supports a conclusion it already reached — it is shown the clauses the
 * rules earned, and nothing else. That is the whole point of grounding the write-up in retrieval built
 * from the deterministic layer's own output, rather than an unconstrained prompt.
 */
public interface KnowledgeRetriever {

    /**
     * The {@code topK} chunks most relevant to {@code firedRuleCodes}, closest first. An empty {@code
     * firedRuleCodes} means no rule fired — there is nothing to ground, so the result is always empty
     * rather than a search over unrelated policy.
     */
    List<RetrievedChunk> retrieve(Collection<String> firedRuleCodes, int topK);
}
