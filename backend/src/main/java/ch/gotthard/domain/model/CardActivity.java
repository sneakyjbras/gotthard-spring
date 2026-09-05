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
 * Card-present or card-not-present activity. Maps {@code card_activity}, the {@code CARD} branch of
 * the {@link Transaction} hierarchy; {@code transaction_id} is both this table's primary key and its
 * foreign key back to {@code transactions}.
 */
@Entity
@Table(name = "card_activity")
@DiscriminatorValue("CARD")
@PrimaryKeyJoinColumn(name = "transaction_id")
public class CardActivity extends Transaction {

    @Column(name = "card_pan", nullable = false, length = 24)
    private String cardPan;

    @Column(name = "card_type", nullable = false, length = 16)
    private String cardType;

    @Column(name = "merchant_name", nullable = false, length = 128)
    private String merchantName;

    // CHAR(4), not VARCHAR: see the note on Customer.country.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "mcc_code", nullable = false, length = 4)
    private String mccCode;

    @Column(name = "card_present", nullable = false)
    private boolean cardPresent;

    @Column(name = "authorization_code", length = 16)
    private String authorizationCode;

    @Column(name = "decline_reason", length = 64)
    private String declineReason;

    protected CardActivity() {
        // JPA
    }

    public CardActivity(
            UUID transactionId,
            Customer customer,
            BigDecimal amount,
            String currency,
            TransactionStatus status,
            OffsetDateTime createdAt,
            String cardPan,
            String cardType,
            String merchantName,
            String mccCode,
            boolean cardPresent,
            String authorizationCode,
            String declineReason) {
        super(transactionId, customer, amount, currency, status, createdAt);
        this.cardPan = cardPan;
        this.cardType = cardType;
        this.merchantName = merchantName;
        this.mccCode = mccCode;
        this.cardPresent = cardPresent;
        this.authorizationCode = authorizationCode;
        this.declineReason = declineReason;
    }

    public String getCardPan() {
        return cardPan;
    }

    public String getCardType() {
        return cardType;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getMccCode() {
        return mccCode;
    }

    public boolean isCardPresent() {
        return cardPresent;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public String getDeclineReason() {
        return declineReason;
    }
}
