package ch.gotthard.ai.retrieval;

import ch.gotthard.domain.repository.PolicyChunkMatch;
import ch.gotthard.domain.repository.PolicyChunkRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link KnowledgeRetriever} backed by pgvector cosine similarity over {@code policy_chunks}.
 *
 * <p><strong>Per-rule query, then merge.</strong> Rather than embedding one blended phrase for every
 * fired rule code and running a single search, this class runs one nearest-neighbour search per fired
 * rule code — each using that rule's own canonical phrase from {@link RuleQueryVocabulary} — and then
 * merges the candidates, keeping each chunk's highest similarity across every rule query that surfaced
 * it. A transaction that fires two unrelated rules (say {@code R-01} and {@code R-06}) should ground
 * its write-up in policy for <em>both</em>, not in whichever topic happens to dominate a single blended
 * bag-of-words query; running the searches separately guarantees each fired rule gets a fair chance to
 * put its best chunk forward before the final ranking truncates to {@code topK}. At the scale this
 * project ever searches — a handful of fired rules against a few dozen chunks — the extra round trips
 * cost nothing worth optimising away.
 */
@Component
public final class PgVectorKnowledgeRetriever implements KnowledgeRetriever {

    /** Candidates pulled per fired rule before merging — generous enough that a rule's best match is
     * never edged out by another rule's chunk before the merge gets to compare them. */
    private static final int CANDIDATES_PER_RULE = 5;

    private final PolicyChunkRepository policyChunks;
    private final EmbeddingModel embeddingModel;

    public PgVectorKnowledgeRetriever(PolicyChunkRepository policyChunks, EmbeddingModel embeddingModel) {
        this.policyChunks = policyChunks;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<RetrievedChunk> retrieve(Collection<String> firedRuleCodes, int topK) {
        if (firedRuleCodes == null || firedRuleCodes.isEmpty() || topK <= 0) {
            return List.of();
        }
        Map<UUID, RetrievedChunk> bestByChunk = new LinkedHashMap<>();
        firedRuleCodes.stream().distinct().forEach(ruleCode -> mergeRuleMatches(ruleCode, bestByChunk));
        return bestByChunk.values().stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::similarity).reversed())
                .limit(topK)
                .toList();
    }

    private void mergeRuleMatches(String ruleCode, Map<UUID, RetrievedChunk> bestByChunk) {
        RuleQueryVocabulary.describe(ruleCode)
                .map(embeddingModel::embed)
                .map(queryVector -> policyChunks.findNearest(queryVector, CANDIDATES_PER_RULE))
                .orElseGet(List::of)
                .forEach(match -> keepBest(bestByChunk, ruleCode, match));
    }

    private static void keepBest(Map<UUID, RetrievedChunk> bestByChunk, String ruleCode, PolicyChunkMatch match) {
        RetrievedChunk candidate = RetrievedChunk.from(ruleCode, match);
        bestByChunk.merge(candidate.chunkId(), candidate, PgVectorKnowledgeRetriever::higherSimilarity);
    }

    private static RetrievedChunk higherSimilarity(RetrievedChunk a, RetrievedChunk b) {
        return a.similarity() >= b.similarity() ? a : b;
    }
}
