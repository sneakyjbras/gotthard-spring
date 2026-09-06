package ch.gotthard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** One search box, two identifiers: the surrogate key and the reference printed on a statement. */
class CustomerSearchServiceTest extends AbstractServiceIntegrationTest {

    @Autowired
    private CustomerSearchService customerSearch;

    @Test
    void given_aCustomerId_when_find_then_thatCustomerComesBack() {
        final UUID customerId = fixtures.customer("CH");

        final CustomerView found = customerSearch.find(customerId.toString());

        assertThat(found.customerId()).isEqualTo(customerId);
        assertThat(found.country()).isEqualTo("CH");
        assertThat(found.segment()).isEqualTo("RETAIL");
    }

    @Test
    void given_aHumanReference_when_find_then_thatCustomerComesBack() {
        final UUID customerId = fixtures.customer("CH");
        final String reference = customerSearch.findById(customerId).reference();

        assertThat(customerSearch.find(reference).customerId()).isEqualTo(customerId);
    }

    /** References are printed in upper case; nobody reading one down a telephone types it that way. */
    @Test
    void given_aReferenceInLowerCase_when_find_then_itStillMatches() {
        final UUID customerId = fixtures.customer("CH");
        final String reference = customerSearch.findById(customerId).reference();

        assertThat(customerSearch
                        .find(reference.toLowerCase(java.util.Locale.ROOT))
                        .customerId())
                .isEqualTo(customerId);
    }

    @Test
    void given_surroundingWhitespace_when_find_then_itStillMatches() {
        final UUID customerId = fixtures.customer("CH");

        assertThat(customerSearch.find("  " + customerId + "  ").customerId()).isEqualTo(customerId);
    }

    @Test
    void given_anUnknownReference_when_find_then_itIsReportedAsMissing() {
        assertThatThrownBy(() -> customerSearch.find("CH-0000-0000"))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("CH-0000-0000");
    }

    @Test
    void given_anUnknownCustomerId_when_findById_then_itIsReportedAsMissing() {
        assertThatThrownBy(() -> customerSearch.findById(UUID.randomUUID()))
                .isInstanceOf(CustomerNotFoundException.class);
    }
}
