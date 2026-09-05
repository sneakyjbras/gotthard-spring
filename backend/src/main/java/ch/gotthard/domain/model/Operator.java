package ch.gotthard.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A bank employee who can look up customers and request AI analyses. Maps {@code operators}.
 *
 * <p>Deliberately has no relationship to {@link OperatorCredentials}: an operator row must be
 * readable without ever pulling in a password hash. Authentication code (owned by {@code
 * security/}) goes through {@code OperatorCredentialsRepository} instead.
 */
@Entity
@Table(name = "operators")
public class Operator {

    @Id
    @Column(name = "operator_id")
    private UUID operatorId;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OperatorRole role;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Operator() {
        // JPA
    }

    public Operator(UUID operatorId, String username, String displayName, OperatorRole role, OffsetDateTime createdAt) {
        this.operatorId = operatorId;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.createdAt = createdAt;
    }

    public UUID getOperatorId() {
        return operatorId;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public OperatorRole getRole() {
        return role;
    }

    public void setRole(OperatorRole role) {
        this.role = role;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Operator that && operatorId != null && operatorId.equals(that.operatorId));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
