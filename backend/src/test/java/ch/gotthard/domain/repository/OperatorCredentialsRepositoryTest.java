package ch.gotthard.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.domain.model.Operator;
import ch.gotthard.domain.model.OperatorCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/** Verifies the shared-primary-key one-to-one: {@code operator_credentials.operator_id} is both its own PK and the FK to {@code operators}. */
class OperatorCredentialsRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OperatorCredentialsRepository credentialsRepository;

    @Test
    void given_credentialsForOperator_when_findByOperatorId_then_resolvesTheSharedKeyAndTheAssociation() {
        Operator operator = DomainFixtures.operator();
        entityManager.persist(operator);
        OperatorCredentials credentials = DomainFixtures.credentials(operator);
        entityManager.persistAndFlush(credentials);
        entityManager.clear();

        var found = credentialsRepository.findById(operator.getOperatorId());

        assertThat(found).isPresent();
        assertThat(found.get().getOperatorId()).isEqualTo(operator.getOperatorId());
        assertThat(found.get().getOperator().getOperatorId()).isEqualTo(operator.getOperatorId());
        assertThat(found.get().getPasswordHash()).isEqualTo("argon2id$dummy-hash");
    }
}
