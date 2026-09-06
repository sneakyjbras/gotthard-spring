package ch.gotthard.domain.repository;

import ch.gotthard.domain.model.PolicyChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyChunkRepository extends JpaRepository<PolicyChunk, UUID>, PolicyChunkRepositoryCustom {

    /** Chunks with no embedding yet — what {@code PolicyCorpusEmbeddingInitializer} fills in at startup. */
    List<PolicyChunk> findByEmbeddingIsNull();
}
