package ch.gotthard.domain.repository;

import ch.gotthard.domain.model.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@code findById(UUID)} covers lookup by surrogate key; {@link #findByReference} covers the human one. */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByReference(String reference);
}
