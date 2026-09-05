package ch.gotthard.domain.repository;

import ch.gotthard.domain.model.Transaction;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Queries the root of the {@link Transaction} hierarchy directly; Hibernate resolves each row to
 * its concrete {@code CardActivity}/{@code PaymentActivity}/{@code CryptoActivity} subtype via the
 * JOINED-table discriminator.
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** A customer's activity in a time window, newest first — mirrors {@code idx_tx_customer_time}. */
    @Query(
            """
            select t from Transaction t
            where t.customer.customerId = :customerId
              and t.createdAt between :from and :to
            order by t.createdAt desc
            """)
    List<Transaction> findByCustomerIdAndCreatedAtBetween(
            @Param("customerId") UUID customerId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
