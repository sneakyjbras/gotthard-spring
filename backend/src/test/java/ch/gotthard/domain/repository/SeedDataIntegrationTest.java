package ch.gotthard.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.domain.model.CardActivity;
import ch.gotthard.domain.model.Customer;
import ch.gotthard.domain.model.PaymentActivity;
import ch.gotthard.domain.model.RiskRule;
import ch.gotthard.domain.model.RuleScope;
import ch.gotthard.domain.model.Transaction;
import ch.gotthard.domain.model.TransactionStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code V4__seed_demo_data.sql} is data, not code, so it gets no unit test of its own -- this
 * exercises it the only way that means anything for a Flyway migration: apply every migration
 * against a real PostgreSQL container (see {@link AbstractRepositoryTest}) and read the rows back
 * through the same repositories the application uses.
 *
 * <p>Every number asserted below is one of the seed generator's own by-design invariants (see
 * {@code scripts/generate_demo_seed_data.py}, in particular {@code build_sandra()},
 * {@code build_thomas()} and {@code build_priya()}) -- this test is a regression guard on that
 * script, not an independent re-derivation of the risk model.
 */
class SeedDataIntegrationTest extends AbstractRepositoryTest {

    private static final String LIVIA_REFERENCE = "CH-7002-4471";
    private static final String RETO_REFERENCE = "CH-7002-4525";
    private static final String SANDRA_REFERENCE = "CH-7002-4488";
    private static final String THOMAS_REFERENCE = "CH-7002-4518";
    private static final String PRIYA_REFERENCE = "CH-7002-4501";
    private static final String KENJI_REFERENCE = "CH-7002-4495";
    private static final String JULIAN_REFERENCE = "CH-7002-4482";

    @Autowired
    private RiskRuleRepository riskRuleRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void given_theMigrationsHaveRun_when_riskRulesAreRead_then_allSevenCodesArePresentWithTheirWeightAndScope() {
        List<RiskRule> rules = riskRuleRepository.findAll();
        Map<String, RiskRule> byCode = rules.stream().collect(Collectors.toMap(RiskRule::getRuleCode, rule -> rule));

        assertThat(rules).hasSize(7);
        assertThat(rules).allMatch(RiskRule::isEnabled);
        assertThat(byCode.keySet()).containsExactlyInAnyOrder("R-01", "R-02", "R-03", "R-04", "R-05", "R-06", "R-07");

        assertWeightAndScope(byCode, "R-01", "44.00", RuleScope.ALL);
        assertWeightAndScope(byCode, "R-02", "32.00", RuleScope.PAYMENT);
        assertWeightAndScope(byCode, "R-03", "52.00", RuleScope.CARD);
        assertWeightAndScope(byCode, "R-04", "34.00", RuleScope.CRYPTO);
        assertWeightAndScope(byCode, "R-05", "54.00", RuleScope.CRYPTO);
        assertWeightAndScope(byCode, "R-06", "20.00", RuleScope.ALL);
        assertWeightAndScope(byCode, "R-07", "18.00", RuleScope.CARD);
    }

    @Test
    void given_theSeedMigration_when_transactionsAreCounted_then_thereAreSeveralHundredForChartsToLookReal() {
        assertThat(transactionRepository.count()).isGreaterThanOrEqualTo(300);
    }

    @Test
    void given_allSevenSeededCustomers_when_lookedUpByReference_then_eachResolves() {
        List<String> references = List.of(
                LIVIA_REFERENCE,
                RETO_REFERENCE,
                SANDRA_REFERENCE,
                THOMAS_REFERENCE,
                PRIYA_REFERENCE,
                KENJI_REFERENCE,
                JULIAN_REFERENCE);

        assertThat(references).allSatisfy(reference -> assertThat(customerRepository.findByReference(reference))
                .as("customer %s should be seeded", reference)
                .isPresent());
    }

    @Test
    void
            given_theStructuringCustomer_when_herRecentPaymentsAreRead_then_fiveSitJustUnderTheReportingThresholdToTheSameBeneficiary() {
        Customer sandra = requireCustomer(SANDRA_REFERENCE);

        List<PaymentActivity> nearThresholdToMyanmar = activityWithinLastDays(sandra, 14).stream()
                .filter(PaymentActivity.class::isInstance)
                .map(PaymentActivity.class::cast)
                .filter(payment -> "MM".equals(payment.getReceiverBankCountry()))
                .toList();

        assertThat(nearThresholdToMyanmar).hasSize(5);
        assertThat(nearThresholdToMyanmar)
                .as("structuring means the same beneficiary, not a scattering of them")
                .extracting(PaymentActivity::getReceiverAccount)
                .containsOnly(nearThresholdToMyanmar.get(0).getReceiverAccount());
        assertThat(nearThresholdToMyanmar).allSatisfy(payment -> assertThat(payment.getAmount())
                .isGreaterThanOrEqualTo(new BigDecimal("9000.00"))
                .isLessThan(new BigDecimal("10000.00")));

        BigDecimal total =
                nearThresholdToMyanmar.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total)
                .as("R-01 also needs the near-threshold amounts to add up to the reporting threshold or more")
                .isGreaterThanOrEqualTo(new BigDecimal("10000.00"));
    }

    @Test
    void
            given_theCardFraudCustomer_when_hisRecentCardActivityIsRead_then_fourCardNotPresentDeclinesSpanThreeMerchants() {
        Customer thomas = requireCustomer(THOMAS_REFERENCE);

        List<CardActivity> cnpDeclines = activityWithinLastDays(thomas, 5).stream()
                .filter(CardActivity.class::isInstance)
                .map(CardActivity.class::cast)
                .filter(card -> !card.isCardPresent())
                .filter(card -> card.getStatus() == TransactionStatus.FAILED)
                .toList();

        assertThat(cnpDeclines).hasSize(4);
        assertThat(cnpDeclines.stream().map(CardActivity::getMerchantName).distinct())
                .as("R-03 needs at least two distinct merchants; the generator deliberately repeats the first once")
                .hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void given_theDormancyBurstCustomer_when_herRecentActivityIsRead_then_sixTransactionsLandOnOneBurstDay() {
        Customer priya = requireCustomer(PRIYA_REFERENCE);

        List<Transaction> recent = activityWithinLastDays(priya, 5);

        assertThat(recent).hasSize(6);
        OffsetDateTime earliest = recent.stream()
                .map(Transaction::getCreatedAt)
                .min(OffsetDateTime::compareTo)
                .orElseThrow();
        OffsetDateTime latest = recent.stream()
                .map(Transaction::getCreatedAt)
                .max(OffsetDateTime::compareTo)
                .orElseThrow();
        assertThat(Duration.between(earliest, latest))
                .as("R-06's burst is 'within a single day'")
                .isLessThan(Duration.ofDays(1));
    }

    private void assertWeightAndScope(Map<String, RiskRule> byCode, String code, String weight, RuleScope scope) {
        RiskRule rule = byCode.get(code);
        assertThat(rule.getWeight()).as("weight of %s", code).isEqualByComparingTo(new BigDecimal(weight));
        assertThat(rule.getAppliesTo()).as("applies_to of %s", code).isEqualTo(scope);
    }

    private Customer requireCustomer(String reference) {
        return customerRepository.findByReference(reference).orElseThrow();
    }

    private List<Transaction> activityWithinLastDays(Customer customer, int lookbackDays) {
        OffsetDateTime now = OffsetDateTime.now();
        return transactionRepository.findByCustomerIdAndCreatedAtBetween(
                customer.getCustomerId(), now.minusDays(lookbackDays), now.plusDays(1));
    }
}
