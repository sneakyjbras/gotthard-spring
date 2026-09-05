package ch.gotthard.security;

import ch.gotthard.domain.model.Operator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * A read-only view over {@code operators} for the one query authentication needs.
 *
 * <p>{@code domain.repository.OperatorRepository} is a bare {@code JpaRepository} with no finder
 * methods, and {@code domain/} belongs to another layer — widening it is not this package's call to
 * make. Spring Data allows more than one repository interface over the same entity, so
 * authentication declares the single derived query it depends on here instead, against the same
 * {@link Operator} entity. Extending {@link Repository} rather than {@code CrudRepository} or
 * {@code JpaRepository} keeps this interface incapable of writing.
 */
interface OperatorLookupRepository extends Repository<Operator, UUID> {

    Optional<Operator> findByUsername(String username);
}
