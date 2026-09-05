package ch.gotthard.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.domain.model.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

class CustomerRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void given_persistedCustomer_when_findById_then_returnsCustomer() {
        Customer customer = DomainFixtures.customer("CH-4410-8821");
        entityManager.persistAndFlush(customer);
        entityManager.clear();

        var found = customerRepository.findById(customer.getCustomerId());

        assertThat(found).isPresent();
        assertThat(found.get().getReference()).isEqualTo("CH-4410-8821");
        assertThat(found.get().getFullName()).isEqualTo("Jane Doe");
        assertThat(found.get().getCountry()).isEqualTo("CH");
        assertThat(found.get().getSegment()).isEqualTo("RETAIL");
    }

    @Test
    void given_persistedCustomer_when_findByReference_then_returnsCustomer() {
        Customer customer = DomainFixtures.customer("CH-9900-1234");
        entityManager.persistAndFlush(customer);
        entityManager.clear();

        var found = customerRepository.findByReference("CH-9900-1234");

        assertThat(found).isPresent();
        assertThat(found.get().getCustomerId()).isEqualTo(customer.getCustomerId());
    }

    @Test
    void given_noCustomerWithThatReference_when_findByReference_then_returnsEmpty() {
        var found = customerRepository.findByReference("CH-0000-0000");

        assertThat(found).isEmpty();
    }
}
