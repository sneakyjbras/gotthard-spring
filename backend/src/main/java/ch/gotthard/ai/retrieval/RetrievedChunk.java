package ch.gotthard.ai.retrieval;

import ch.gotthard.domain.repository.PolicyChunkMatch;
import java.util.UUID;

/**
 * One policy chunk handed back by a {@link KnowledgeRetriever}: the text, the cosine similarity that
 * earned its place, and the fired rule code whose query found it.
 */
public record RetrievedChunk(
        UUID chunkId,
        String document,
        String title,
        String section,
        String body,
        double similarity,
        String firedRuleCode) {

    static RetrievedChunk from(final String firedRuleCode, final PolicyChunkMatch match) {
        return new RetrievedChunk(
                match.chunkId(),
                match.document(),
                match.title(),
                match.section(),
                match.body(),
                match.similarity(),
                firedRuleCode);
    }
}
