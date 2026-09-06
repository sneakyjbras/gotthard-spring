package ch.gotthard.domain.repository;

import java.util.List;

/**
 * The one {@code policy_chunks} query {@link org.springframework.data.jpa.repository.JpaRepository}
 * cannot express: nearest neighbours by pgvector cosine distance, ranked and limited in the database
 * rather than pulled into Java to sort. Implemented by {@link PolicyChunkRepositoryImpl}; Spring Data
 * wires that implementation into {@link PolicyChunkRepository} by its {@code Impl} suffix, the
 * standard "custom repository fragment" convention — no extra configuration needed.
 */
interface PolicyChunkRepositoryCustom {

    /**
     * The {@code limit} chunks whose embedding is closest to {@code queryVector} by cosine similarity,
     * closest first. Chunks with no embedding yet are never returned.
     */
    List<PolicyChunkMatch> findNearest(float[] queryVector, int limit);
}
