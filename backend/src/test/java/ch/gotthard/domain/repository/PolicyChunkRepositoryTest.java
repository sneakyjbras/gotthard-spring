package ch.gotthard.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.domain.model.PolicyChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * {@link PolicyChunk#getEmbedding()} is a full read-write mapping over the pgvector column via
 * {@code SqlTypes.VECTOR_FLOAT32} — see the class Javadoc for how that fix works and why it replaced
 * the placeholder {@code String} mapping this test used to describe. The first test below proves a
 * chunk with no embedding yet (exactly what {@code V3__seed_policy_corpus.sql} inserts, before {@code
 * ai.retrieval.PolicyCorpusEmbeddingInitializer} runs) still round-trips cleanly; the second proves the
 * mapping now genuinely writes and reads a real vector, which is what "properly" fixing it meant.
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

    @Test
    void given_chunkWithARealEmbedding_when_savedThroughTheRepositoryAndReread_then_theVectorRoundTripsExactly() {
        PolicyChunk chunk = DomainFixtures.policyChunk();
        float[] embedding = new float[384];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) Math.sin(i);
        }
        chunk.setEmbedding(embedding);

        policyChunkRepository.saveAndFlush(chunk);
        entityManager.clear();

        PolicyChunk found = policyChunkRepository.findById(chunk.getChunkId()).orElseThrow();

        assertThat(found.getEmbedding()).hasSize(384).containsExactly(embedding);
    }
}
