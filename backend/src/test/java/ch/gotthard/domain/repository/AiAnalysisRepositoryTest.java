package ch.gotthard.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.domain.model.AiAnalysis;
import ch.gotthard.domain.model.Customer;
import ch.gotthard.domain.model.Operator;
import ch.gotthard.domain.model.RiskLevel;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

class AiAnalysisRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AiAnalysisRepository aiAnalysisRepository;

    @Test
    void given_multipleAnalysesForCustomer_when_findByCustomerId_then_returnsNewestFirstExcludingOtherCustomers() {
        Customer customer = DomainFixtures.customer();
        Operator operator = DomainFixtures.operator();
        entityManager.persist(customer);
        entityManager.persist(operator);
        Customer otherCustomer = DomainFixtures.customer();
        entityManager.persist(otherCustomer);

        OffsetDateTime now = OffsetDateTime.now();
        AiAnalysis oldest = DomainFixtures.aiAnalysis(customer, operator, now.minusDays(10));
        AiAnalysis newest = DomainFixtures.aiAnalysis(customer, operator, now);
        AiAnalysis forOtherCustomer = DomainFixtures.aiAnalysis(otherCustomer, operator, now);
        entityManager.persist(oldest);
        entityManager.persist(newest);
        entityManager.persistAndFlush(forOtherCustomer);
        entityManager.clear();

        List<AiAnalysis> found = aiAnalysisRepository.findByCustomerId(customer.getCustomerId());

        assertThat(found)
                .extracting(AiAnalysis::getAnalysisId)
                .containsExactly(newest.getAnalysisId(), oldest.getAnalysisId());
    }

    @Test
    void given_computedAndAssessedLevelsDiffer_when_reloaded_then_levelsDivergedIsTrue() {
        Customer customer = DomainFixtures.customer();
        Operator operator = DomainFixtures.operator();
        entityManager.persist(customer);
        entityManager.persist(operator);
        AiAnalysis diverged =
                DomainFixtures.aiAnalysis(customer, operator, OffsetDateTime.now(), RiskLevel.LOW, RiskLevel.HIGH);
        entityManager.persistAndFlush(diverged);
        entityManager.clear();

        AiAnalysis reloaded =
                aiAnalysisRepository.findById(diverged.getAnalysisId()).orElseThrow();

        assertThat(reloaded.getComputedLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(reloaded.getAssessedLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(reloaded.isLevelsDiverged()).isTrue();
    }

    @Test
    void given_computedAndAssessedLevelsMatch_when_reloaded_then_levelsDivergedIsFalse() {
        Customer customer = DomainFixtures.customer();
        Operator operator = DomainFixtures.operator();
        entityManager.persist(customer);
        entityManager.persist(operator);
        AiAnalysis agreeing =
                DomainFixtures.aiAnalysis(customer, operator, OffsetDateTime.now(), RiskLevel.MEDIUM, RiskLevel.MEDIUM);
        entityManager.persistAndFlush(agreeing);
        entityManager.clear();

        AiAnalysis reloaded =
                aiAnalysisRepository.findById(agreeing.getAnalysisId()).orElseThrow();

        assertThat(reloaded.isLevelsDiverged()).isFalse();
    }
}
