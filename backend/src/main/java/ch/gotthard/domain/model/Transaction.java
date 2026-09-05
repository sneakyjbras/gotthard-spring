package ch.gotthard.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One unit of customer activity. Maps {@code transactions}, the root of a JOINED-table inheritance
 * hierarchy discriminated on {@code activity_type}: {@link CardActivity}, {@link PaymentActivity}
 * and {@link CryptoActivity} each add their own table, keyed by the same {@code transaction_id}.
 *
 * <p>Abstract on purpose — {@code activity_type} has no fourth, "generic" value, so every row in
 * this table is required to have a matching row in exactly one subtype table. There is no such
 * thing as a bare {@code Transaction}.
 *
 * <p>Queried by customer and time window via {@code idx_tx_customer_time}; the full table is also
 * BRIN-indexed on {@code created_at} ({@code idx_tx_created_brin}) since the data is append-only and
 * time-ordered. Both are physical indexes from the migration — nothing to map here, they apply
 * automatically to whatever SQL the repository issues.
 */
@Entity
@Table(name = "transactions")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "activity_type", discriminatorType = DiscriminatorType.STRING, length = 8)
public abstract class Transaction {

    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Transaction() {
        // JPA
    }

    protected Transaction(
            UUID transactionId,
            Customer customer,
            BigDecimal amount,
            String currency,
            TransactionStatus status,
            OffsetDateTime createdAt) {
        this.transactionId = transactionId;
        this.customer = customer;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    /** A transaction's lifecycle status is the one field on this hierarchy that legitimately changes. */
    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o
                || (o instanceof Transaction that && transactionId != null && transactionId.equals(that.transactionId));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
