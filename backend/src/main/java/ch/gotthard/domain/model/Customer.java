package ch.gotthard.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One customer of the bank. Maps {@code customers}.
 *
 * <p>Identity ({@link #reference}, {@link #fullName}, {@link #country}) and onboarding date are
 * fixed at creation. {@link #segment} is the one field expected to change over a customer's
 * lifetime, so it is the only one with a setter.
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    @Column(name = "full_name", nullable = false, length = 128)
    private String fullName;

    // CHAR(2), not VARCHAR: Hibernate's schema validator treats them as distinct JDBC types
    // (Postgres reports CHAR as bpchar), so the JDBC type must be pinned explicitly.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 2)
    private String country;

    @Column(nullable = false, length = 32)
    private String segment;

    @Column(name = "onboarded_at", nullable = false)
    private OffsetDateTime onboardedAt;

    protected Customer() {
        // JPA
    }

    public Customer(
            UUID customerId,
            String reference,
            String fullName,
            String country,
            String segment,
            OffsetDateTime onboardedAt) {
        this.customerId = customerId;
        this.reference = reference;
        this.fullName = fullName;
        this.country = country;
        this.segment = segment;
        this.onboardedAt = onboardedAt;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getReference() {
        return reference;
    }

    public String getFullName() {
        return fullName;
    }

    public String getCountry() {
        return country;
    }

    public String getSegment() {
        return segment;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }

    public OffsetDateTime getOnboardedAt() {
        return onboardedAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Customer that && customerId != null && customerId.equals(that.customerId));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
