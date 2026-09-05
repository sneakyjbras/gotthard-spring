package ch.gotthard.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.domain.model.AiAnalysis;
import ch.gotthard.domain.model.AiAnalysisCitation;
import ch.gotthard.domain.model.AiAnalysisCitationId;
import ch.gotthard.domain.model.Customer;
import ch.gotthard.domain.model.Operator;
import ch.gotthard.domain.model.PolicyChunk;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

class AiAnalysisCitationRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AiAnalysisCitationRepository citationRepository;

    @Test
    void given_citationLinkingAnalysisAndChunk_when_findByCompositeId_then_resolvesBothAssociations() {
        Customer customer = DomainFixtures.customer();
        Operator operator = DomainFixtures.operator();
        entityManager.persist(customer);
        entityManager.persist(operator);
        AiAnalysis analysis = DomainFixtures.aiAnalysis(customer, operator, OffsetDateTime.now());
        entityManager.persist(analysis);
        PolicyChunk chunk = DomainFixtures.policyChunk();
        entityManager.persist(chunk);
        AiAnalysisCitation citation = DomainFixtures.citation(analysis, chunk, 0.87f, (short) 1);
        entityManager.persistAndFlush(citation);
        entityManager.clear();

        AiAnalysisCitationId id = new AiAnalysisCitationId(analysis.getAnalysisId(), chunk.getChunkId());
        AiAnalysisCitation found = citationRepository.findById(id).orElseThrow();

        assertThat(found.getAnalysis().getAnalysisId()).isEqualTo(analysis.getAnalysisId());
        assertThat(found.getChunk().getChunkId()).isEqualTo(chunk.getChunkId());
        assertThat(found.getSimilarity()).isEqualTo(0.87f);
        assertThat(found.getRank()).isEqualTo((short) 1);
    }
}
