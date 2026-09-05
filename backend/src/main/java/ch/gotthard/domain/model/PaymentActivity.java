package ch.gotthard.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A bank transfer. Maps {@code payment_activity}, the {@code PAYMENT} branch of the
 * {@link Transaction} hierarchy; {@code transaction_id} is both this table's primary key and its
 * foreign key back to {@code transactions}.
 *
 * <p>{@code receiver_account} is indexed ({@code idx_payment_beneficiary}) for counterparty-corridor
 * lookups; that index needs no mapping here, it applies to whatever SQL the repository issues.
 */
@Entity
@Table(name = "payment_activity")
@DiscriminatorValue("PAYMENT")
@PrimaryKeyJoinColumn(name = "transaction_id")
public class PaymentActivity extends Transaction {

    @Column(name = "payment_method", nullable = false, length = 16)
    private String paymentMethod;

    @Column(name = "sender_account", nullable = false, length = 34)
    private String senderAccount;

    @Column(name = "receiver_account", nullable = false, length = 34)
    private String receiverAccount;

    // CHAR(2), not VARCHAR: see the note on Customer.country.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "receiver_bank_country", nullable = false, length = 2)
    private String receiverBankCountry;

    protected PaymentActivity() {
        // JPA
    }

    public PaymentActivity(
            UUID transactionId,
            Customer customer,
            BigDecimal amount,
            String currency,
            TransactionStatus status,
            OffsetDateTime createdAt,
            String paymentMethod,
            String senderAccount,
            String receiverAccount,
            String receiverBankCountry) {
        super(transactionId, customer, amount, currency, status, createdAt);
        this.paymentMethod = paymentMethod;
        this.senderAccount = senderAccount;
        this.receiverAccount = receiverAccount;
        this.receiverBankCountry = receiverBankCountry;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getSenderAccount() {
        return senderAccount;
    }

    public String getReceiverAccount() {
        return receiverAccount;
    }

    public String getReceiverBankCountry() {
        return receiverBankCountry;
    }
}
