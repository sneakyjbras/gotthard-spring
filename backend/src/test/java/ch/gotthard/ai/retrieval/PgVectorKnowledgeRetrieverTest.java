package ch.gotthard.ai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.TestcontainersConfiguration;
import ch.gotthard.domain.model.PolicyChunk;
import ch.gotthard.domain.repository.PolicyChunkRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * Exercises {@link PgVectorKnowledgeRetriever} against the real corpus {@code
 * V3__seed_policy_corpus.sql} seeds, over a real PostgreSQL + pgvector container (never H2 — the
 * {@code <=>} cosine-distance operator this class's query depends on is a pgvector extension).
 *
 * <p>{@code @DataJpaTest} rather than the shared {@code AbstractRepositoryTest} in {@code
 * domain.repository}: that base class is package-private by design (repository tests own their
 * package), so this test restates the same three annotations directly. {@link
 * PgVectorKnowledgeRetriever} is built by hand rather than autowired, since a JPA slice context does
 * not component-scan plain {@code @Component} beans — a {@link HashingEmbeddingModel} needs no
 * container to construct anyway.
 *
 * <p>Flyway seeds {@code policy_chunks} with text and a {@code NULL} embedding; the {@code
 * ApplicationRunner} that would normally fill it in does not run in a JPA slice, so {@link
 * #embedTheSeededCorpus()} does that part by hand before every test, then flushes so the raw JDBC
 * query in {@code PolicyChunkRepositoryImpl} — which bypasses the Hibernate session entirely — can
 * actually see the written vectors.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class PgVectorKnowledgeRetrieverTest {

    @Autowired
    private PolicyChunkRepository policyChunkRepository;

    private KnowledgeRetriever retriever;

    @BeforeEach
    void embedTheSeededCorpus() {
        EmbeddingModel embeddingModel = new HashingEmbeddingModel();
        List<PolicyChunk> unembedded = policyChunkRepository.findByEmbeddingIsNull();
        unembedded.forEach(chunk -> chunk.setEmbedding(embeddingModel.embed(chunk.getBody())));
        policyChunkRepository.saveAll(unembedded);
        policyChunkRepository.flush();
        retriever = new PgVectorKnowledgeRetriever(policyChunkRepository, embeddingModel);
    }

    @Test
    void given_theCorpusIsSeeded_when_embedded_then_everyChunkHasA384DimensionEmbedding() {
        assertThat(policyChunkRepository.findByEmbeddingIsNull()).isEmpty();
        assertThat(policyChunkRepository.findAll()).isNotEmpty().allSatisfy(chunk -> assertThat(chunk.getEmbedding())
                .hasSize(HashingEmbeddingModel.DIMENSIONS));
    }

    @Test
    void given_theCrossBorderCorridorRuleFired_when_retrieve_then_topHitIsTheSanctionsDetectionCriteriaChunk() {
        List<RetrievedChunk> results = retriever.retrieve(List.of("R-02"), 3);

        assertThat(results).isNotEmpty();
        RetrievedChunk top = results.get(0);
        assertThat(top.document()).isEqualTo("SANCTIONS-01");
        assertThat(top.section()).isEqualTo("3");
        assertThat(top.firedRuleCode()).isEqualTo("R-02");
    }

    @Test
    void given_theCardTestingRuleFired_when_retrieve_then_topHitIsTheFraudDetectionCriteriaChunk() {
        List<RetrievedChunk> results = retriever.retrieve(List.of("R-03"), 3);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).document()).isEqualTo("FRAUD-01");
        assertThat(results.get(0).section()).isEqualTo("3");
    }

    @Test
    void given_theDormancyBurstRuleFired_when_retrieve_then_topHitIsTheDormancyDetectionCriteriaChunk() {
        List<RetrievedChunk> results = retriever.retrieve(List.of("R-06"), 3);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).document()).isEqualTo("AML-002");
        assertThat(results.get(0).section()).isEqualTo("3");
    }

    @Test
    void given_multipleFiredRules_when_retrieve_then_resultsAreOrderedBySimilarityDescending() {
        List<RetrievedChunk> results = retriever.retrieve(List.of("R-01", "R-02", "R-06"), 10);

        assertThat(results.size()).isGreaterThan(2);
        assertThat(results).extracting(RetrievedChunk::similarity).isSortedAccordingTo((a, b) -> Double.compare(b, a));
    }

    @Test
    void given_twoUnrelatedFiredRules_when_retrieve_then_bothTopicsAreRepresentedAmongTheResults() {
        List<RetrievedChunk> results = retriever.retrieve(List.of("R-01", "R-06"), 8);

        assertThat(results).extracting(RetrievedChunk::document).contains("AML-001", "AML-002");
    }

    @Test
    void given_noFiredRules_when_retrieve_then_returnsEmpty() {
        assertThat(retriever.retrieve(List.of(), 5)).isEmpty();
    }

    @Test
    void given_aNonPositiveTopK_when_retrieve_then_returnsEmpty() {
        assertThat(retriever.retrieve(List.of("R-01"), 0)).isEmpty();
    }

    @Test
    void given_anUnknownRuleCode_when_retrieve_then_returnsEmptyRatherThanThrowing() {
        assertThat(retriever.retrieve(List.of("R-99"), 5)).isEmpty();
    }
}
