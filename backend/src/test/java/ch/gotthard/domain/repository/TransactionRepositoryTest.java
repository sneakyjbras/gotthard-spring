package ch.gotthard.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ch.gotthard.domain.model.CardActivity;
import ch.gotthard.domain.model.CryptoActivity;
import ch.gotthard.domain.model.Customer;
import ch.gotthard.domain.model.PaymentActivity;
import ch.gotthard.domain.model.Transaction;
import ch.gotthard.domain.model.TransactionStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * Exercises the JOINED-table inheritance hierarchy: each subtype round-trips through its own table
 * plus the shared {@code transactions} table, and the base-class query resolves the correct concrete
 * type for each row via the {@code activity_type} discriminator.
 */
class TransactionRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void given_cardActivity_when_findById_then_returnsCardActivityWithInheritedAndOwnFields() {
        Customer customer = DomainFixtures.customer();
        entityManager.persist(customer);
        CardActivity card = DomainFixtures.cardActivity(customer, OffsetDateTime.now());
        entityManager.persistAndFlush(card);
        entityManager.clear();

        Transaction found =
                transactionRepository.findById(card.getTransactionId()).orElseThrow();

        assertThat(found).isInstanceOf(CardActivity.class);
        assertThat(found.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(found.getCustomer().getCustomerId()).isEqualTo(customer.getCustomerId());
        CardActivity foundCard = (CardActivity) found;
        assertThat(foundCard.getCardPan()).isEqualTo("4111111111111111");
        assertThat(foundCard.getMerchantName()).isEqualTo("Coop Zurich");
        assertThat(foundCard.isCardPresent()).isTrue();
        assertThat(foundCard.getDeclineReason()).isNull();
    }

    @Test
    void given_paymentActivity_when_findById_then_returnsPaymentActivityWithOwnFields() {
        Customer customer = DomainFixtures.customer();
        entityManager.persist(customer);
        PaymentActivity payment = DomainFixtures.paymentActivity(customer, OffsetDateTime.now());
        entityManager.persistAndFlush(payment);
        entityManager.clear();

        Transaction found =
                transactionRepository.findById(payment.getTransactionId()).orElseThrow();

        assertThat(found).isInstanceOf(PaymentActivity.class);
        PaymentActivity foundPayment = (PaymentActivity) found;
        assertThat(foundPayment.getReceiverAccount()).isEqualTo("DE89370400440532013000");
        assertThat(foundPayment.getReceiverBankCountry()).isEqualTo("DE");
    }

    @Test
    void given_cryptoActivity_when_findById_then_returnsCryptoActivityWithOwnFields() {
        Customer customer = DomainFixtures.customer();
        entityManager.persist(customer);
        CryptoActivity crypto = DomainFixtures.cryptoActivity(customer, OffsetDateTime.now());
        entityManager.persistAndFlush(crypto);
        entityManager.clear();

        Transaction found =
                transactionRepository.findById(crypto.getTransactionId()).orElseThrow();

        assertThat(found).isInstanceOf(CryptoActivity.class);
        CryptoActivity foundCrypto = (CryptoActivity) found;
        assertThat(foundCrypto.getBlockchain()).isEqualTo("BITCOIN");
        assertThat(foundCrypto.getTxHash()).isEqualTo("abc123hash");
    }

    @Test
    void
            given_transactionsInAndOutOfWindow_when_findByCustomerIdAndCreatedAtBetween_then_returnsOnlyWindowNewestFirst() {
        Customer customer = DomainFixtures.customer();
        entityManager.persist(customer);
        Customer otherCustomer = DomainFixtures.customer();
        entityManager.persist(otherCustomer);

        OffsetDateTime now = OffsetDateTime.now();
        CardActivity inWindowOld = DomainFixtures.cardActivity(customer, now.minusDays(5));
        CardActivity inWindowNew = DomainFixtures.cardActivity(customer, now.minusDays(1));
        PaymentActivity beforeWindow = DomainFixtures.paymentActivity(customer, now.minusDays(20));
        CryptoActivity afterWindow = DomainFixtures.cryptoActivity(customer, now.plusDays(5));
        CardActivity otherCustomerInWindow = DomainFixtures.cardActivity(otherCustomer, now.minusDays(2));
        entityManager.persist(inWindowOld);
        entityManager.persist(inWindowNew);
        entityManager.persist(beforeWindow);
        entityManager.persist(afterWindow);
        entityManager.persistAndFlush(otherCustomerInWindow);
        entityManager.clear();

        List<Transaction> found = transactionRepository.findByCustomerIdAndCreatedAtBetween(
                customer.getCustomerId(), now.minusDays(10), now);

        assertThat(found)
                .extracting(Transaction::getTransactionId)
                .containsExactly(inWindowNew.getTransactionId(), inWindowOld.getTransactionId());
    }
}
