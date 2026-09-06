package ch.gotthard.ai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.TestcontainersConfiguration;
import ch.gotthard.domain.model.PolicyChunk;
import ch.gotthard.domain.repository.PolicyChunkRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves the production startup path, not just {@link PgVectorKnowledgeRetriever} in isolation: a
 * real {@code SpringApplication} boot runs {@link PolicyCorpusEmbeddingInitializer} as an {@code
 * ApplicationRunner} automatically, so by the time this test body runs, Flyway has already seeded
 * {@code policy_chunks} with text ({@code V3__seed_policy_corpus.sql}) and the initializer has already
 * embedded every row — no manual embedding step, unlike {@link PgVectorKnowledgeRetrieverTest}, which
 * uses the {@code @DataJpaTest} slice specifically because slice tests do *not* run {@code
 * ApplicationRunner} beans.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PolicyCorpusEmbeddingInitializerTest {

    @Autowired
    private PolicyChunkRepository policyChunkRepository;

    @Test
    void given_applicationHasStarted_when_policyChunksAreRead_then_everySeededChunkIsAlreadyEmbedded() {
        List<PolicyChunk> chunks = policyChunkRepository.findAll();

        assertThat(chunks).isNotEmpty();
        assertThat(policyChunkRepository.findByEmbeddingIsNull()).isEmpty();
        assertThat(chunks)
                .allSatisfy(chunk -> assertThat(chunk.getEmbedding()).hasSize(HashingEmbeddingModel.DIMENSIONS));
    }

    @Test
    void given_theCorpusDocuments_when_read_then_allNineDocumentsAndFiftyFourSectionsArePresent() {
        List<PolicyChunk> chunks = policyChunkRepository.findAll();
        List<String> distinctDocuments =
                chunks.stream().map(PolicyChunk::getDocument).distinct().toList();

        assertThat(chunks).hasSize(54);
        assertThat(distinctDocuments)
                .containsExactlyInAnyOrder(
                        "AML-001",
                        "AML-002",
                        "AML-003",
                        "SANCTIONS-01",
                        "FRAUD-01",
                        "CRYPTO-01",
                        "CRYPTO-02",
                        "SOP-01",
                        "GOV-01");
    }
}
