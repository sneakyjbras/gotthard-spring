package ch.gotthard.service;

import ch.gotthard.domain.model.Customer;
import ch.gotthard.domain.repository.CustomerRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Find a customer by whichever identifier the operator has to hand.
 *
 * <p>Two identifiers, one search box. {@code customer_id} is what the rest of the system passes
 * around; {@code reference} — {@code CH-4410-8821} — is what is printed on a statement and read down
 * a telephone. Which one was typed is not a question worth asking an operator, so the input is
 * simply tried as a UUID first and treated as a reference when it is not one.
 */
@Service
public class CustomerSearchService {

    private final CustomerRepository customers;

    public CustomerSearchService(final CustomerRepository customers) {
        this.customers = customers;
    }

    /** The customer that identifier names, or {@link CustomerNotFoundException}. */
    @Transactional(readOnly = true)
    public CustomerView find(final String idOrReference) {
        return viewOf(require(idOrReference));
    }

    /** The customer behind an id known to be a UUID — how the other use cases start. */
    @Transactional(readOnly = true)
    public CustomerView findById(final UUID customerId) {
        return viewOf(require(customerId));
    }

    Customer require(final UUID customerId) {
        return customers
                .findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(String.valueOf(customerId)));
    }

    private Customer require(final String idOrReference) {
        return locate(idOrReference).orElseThrow(() -> new CustomerNotFoundException(idOrReference));
    }

    private Optional<Customer> locate(final String idOrReference) {
        final String trimmed = idOrReference.trim();
        return asUuid(trimmed).map(customers::findById).orElseGet(() -> byReference(trimmed));
    }

    /** References are printed in upper case, but nobody types them that way over the telephone. */
    private Optional<Customer> byReference(final String reference) {
        return customers
                .findByReference(reference)
                .or(() -> customers.findByReference(reference.toUpperCase(Locale.ROOT)));
    }

    private static Optional<UUID> asUuid(final String candidate) {
        try {
            return Optional.of(UUID.fromString(candidate));
        } catch (final IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    static CustomerView viewOf(final Customer customer) {
        return new CustomerView(
                customer.getCustomerId(),
                customer.getReference(),
                customer.getFullName(),
                customer.getCountry(),
                customer.getSegment(),
                customer.getOnboardedAt());
    }
}
