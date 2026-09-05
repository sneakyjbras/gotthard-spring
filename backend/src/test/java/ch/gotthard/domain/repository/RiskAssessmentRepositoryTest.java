package ch.gotthard.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.domain.model.CardActivity;
import ch.gotthard.domain.model.Customer;
import ch.gotthard.domain.model.RiskAssessment;
import ch.gotthard.domain.model.RiskRule;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

class RiskAssessmentRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RiskAssessmentRepository riskAssessmentRepository;

    @Test
    void given_ruleFiredOnTransaction_when_findById_then_resolvesBothAssociations() {
        Customer customer = DomainFixtures.customer();
        entityManager.persist(customer);
        CardActivity transaction = DomainFixtures.cardActivity(customer, OffsetDateTime.now());
        entityManager.persist(transaction);
        RiskRule rule = DomainFixtures.riskRule();
        entityManager.persist(rule);
        RiskAssessment assessment = DomainFixtures.riskAssessment(transaction, rule);
        entityManager.persistAndFlush(assessment);
        entityManager.clear();

        RiskAssessment found =
                riskAssessmentRepository.findById(assessment.getAssessmentId()).orElseThrow();

        assertThat(found.getTransaction().getTransactionId()).isEqualTo(transaction.getTransactionId());
        assertThat(found.getRule().getRuleId()).isEqualTo(rule.getRuleId());
        assertThat(found.getScoreContribution()).isEqualByComparingTo(new BigDecimal("12.50"));
    }
}
