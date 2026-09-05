package ch.gotthard.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.domain.model.Operator;
import ch.gotthard.domain.model.OperatorRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

class OperatorRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OperatorRepository operatorRepository;

    @Test
    void given_persistedOperator_when_findById_then_returnsOperator() {
        Operator operator = DomainFixtures.operator();
        entityManager.persistAndFlush(operator);
        entityManager.clear();

        var found = operatorRepository.findById(operator.getOperatorId());

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo(operator.getUsername());
        assertThat(found.get().getDisplayName()).isEqualTo("Jane Operator");
        assertThat(found.get().getRole()).isEqualTo(OperatorRole.OPERATOR);
    }
}
