package ch.gotthard.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.domain.model.RiskRule;
import ch.gotthard.domain.model.RuleScope;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

class RiskRuleRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RiskRuleRepository riskRuleRepository;

    @Test
    void given_persistedRule_when_findById_then_returnsRuleWithScopeAndWeight() {
        RiskRule rule = DomainFixtures.riskRule();
        entityManager.persistAndFlush(rule);
        entityManager.clear();

        RiskRule found = riskRuleRepository.findById(rule.getRuleId()).orElseThrow();

        assertThat(found.getRuleCode()).isEqualTo(rule.getRuleCode());
        assertThat(found.getAppliesTo()).isEqualTo(RuleScope.ALL);
        assertThat(found.getWeight()).isEqualByComparingTo(new BigDecimal("12.50"));
        assertThat(found.isEnabled()).isTrue();
    }
}
