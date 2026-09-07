package ch.gotthard.domain.repository;

import ch.gotthard.domain.model.Operator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code findById(UUID)} covers lookup by surrogate key; {@link #findByUsername} covers the one an
 * authenticated session actually carries.
 *
 * <p>A session's principal is a username, not an id — so attributing an AI analysis to the operator
 * who asked for it starts here. {@code security.OperatorLookupRepository} declares the same derived
 * query over the same entity for authentication; that one is deliberately incapable of writing,
 * which is why it is not simply reused by a use case that has to attach the row it finds to an
 * entity it is about to persist.
 */
public interface OperatorRepository extends JpaRepository<Operator, UUID> {

    Optional<Operator> findByUsername(String username);
}
