package ch.gotthard.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A cryptocurrency movement. Maps {@code crypto_activity}, the {@code CRYPTO} branch of the
 * {@link Transaction} hierarchy; {@code transaction_id} is both this table's primary key and its
 * foreign key back to {@code transactions}.
 *
 * <p>Both wallet columns are indexed ({@code idx_crypto_from}, {@code idx_crypto_to}) since the
 * wallet graph is traversed from either end during BFS; those indexes need no mapping here, they
 * apply to whatever SQL the repository issues.
 */
@Entity
@Table(name = "crypto_activity")
@DiscriminatorValue("CRYPTO")
@PrimaryKeyJoinColumn(name = "transaction_id")
public class CryptoActivity extends Transaction {

    @Column(nullable = false, length = 16)
    private String blockchain;

    @Column(name = "wallet_address_from", nullable = false, length = 128)
    private String walletAddressFrom;

    @Column(name = "wallet_address_to", nullable = false, length = 128)
    private String walletAddressTo;

    @Column(name = "tx_hash", nullable = false, length = 128)
    private String txHash;

    @Column(name = "exchange_name", length = 64)
    private String exchangeName;

    protected CryptoActivity() {
        // JPA
    }

    public CryptoActivity(
            UUID transactionId,
            Customer customer,
            BigDecimal amount,
            String currency,
            TransactionStatus status,
            OffsetDateTime createdAt,
            String blockchain,
            String walletAddressFrom,
            String walletAddressTo,
            String txHash,
            String exchangeName) {
        super(transactionId, customer, amount, currency, status, createdAt);
        this.blockchain = blockchain;
        this.walletAddressFrom = walletAddressFrom;
        this.walletAddressTo = walletAddressTo;
        this.txHash = txHash;
        this.exchangeName = exchangeName;
    }

    public String getBlockchain() {
        return blockchain;
    }

    public String getWalletAddressFrom() {
        return walletAddressFrom;
    }

    public String getWalletAddressTo() {
        return walletAddressTo;
    }

    public String getTxHash() {
        return txHash;
    }

    public String getExchangeName() {
        return exchangeName;
    }
}
