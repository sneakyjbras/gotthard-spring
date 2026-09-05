package ch.gotthard.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The argon2id hash for one {@link Operator}, kept in its own table so operator rows can be read
 * without ever loading a hash. Maps {@code operator_credentials}, a strict one-to-one on a shared
 * primary key ({@code operator_id} is both this table's PK and its FK to {@code operators}).
 *
 * <p>{@code security/} owns hashing and verification; this class only persists the result.
 */
@Entity
@Table(name = "operator_credentials")
public class OperatorCredentials {

    @Id
    @Column(name = "operator_id")
    private UUID operatorId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "operator_id")
    private Operator operator;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected OperatorCredentials() {
        // JPA
    }

    public OperatorCredentials(Operator operator, String passwordHash, OffsetDateTime updatedAt) {
        this.operator = operator;
        this.passwordHash = passwordHash;
        this.updatedAt = updatedAt;
    }

    public UUID getOperatorId() {
        return operatorId;
    }

    public Operator getOperator() {
        return operator;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o
                || (o instanceof OperatorCredentials that && operatorId != null && operatorId.equals(that.operatorId));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
