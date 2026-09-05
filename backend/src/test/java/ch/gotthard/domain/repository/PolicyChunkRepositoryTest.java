package ch.gotthard.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.domain.model.PolicyChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * {@link PolicyChunk#getEmbedding()} is a placeholder mapping over the pgvector column — see the
 * class Javadoc. This test only proves the row round-trips with a null embedding, which is all the
 * mapping is required to do; retrieval owns writing real vectors.
 */
class PolicyChunkRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PolicyChunkRepository policyChunkRepository;

    @Test
    void given_chunkWithNullEmbedding_when_findById_then_roundTripsTextAndJsonbMetadata() {
        PolicyChunk chunk = DomainFixtures.policyChunk();
        entityManager.persistAndFlush(chunk);
        entityManager.clear();

        PolicyChunk found = policyChunkRepository.findById(chunk.getChunkId()).orElseThrow();

        assertThat(found.getDocument()).isEqualTo("AML-004");
        assertThat(found.getTitle()).isEqualTo("Threshold reporting");
        assertThat(found.getBody()).isEqualTo("Report cash transactions over CHF 15'000.");
        assertThat(found.getEmbedding()).isNull();
        assertThat(found.getMetadata()).isEqualTo("{}");
    }
}
