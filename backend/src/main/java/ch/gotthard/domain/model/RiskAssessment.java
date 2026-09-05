package ch.gotthard.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One fired rule against one transaction. Maps {@code risk_assessments} — the audit trail: every
 * rule that fires writes a row here, and the row is never edited afterwards, so this entity exposes
 * no setters.
 *
 * <p>{@code transactions.transaction_id} carries {@code ON DELETE CASCADE} into this table; that is
 * enforced by PostgreSQL itself and needs no Hibernate-side cascade to match.
 */
@Entity
@Table(name = "risk_assessments")
public class RiskAssessment {

    @Id
    @Column(name = "assessment_id")
    private UUID assessmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private RiskRule rule;

    @Column(name = "triggered_at", nullable = false)
    private OffsetDateTime triggeredAt;

    @Column(name = "score_contribution", nullable = false, precision = 5, scale = 2)
    private BigDecimal scoreContribution;

    protected RiskAssessment() {
        // JPA
    }

    public RiskAssessment(
            UUID assessmentId,
            Transaction transaction,
            RiskRule rule,
            OffsetDateTime triggeredAt,
            BigDecimal scoreContribution) {
        this.assessmentId = assessmentId;
        this.transaction = transaction;
        this.rule = rule;
        this.triggeredAt = triggeredAt;
        this.scoreContribution = scoreContribution;
    }

    public UUID getAssessmentId() {
        return assessmentId;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public RiskRule getRule() {
        return rule;
    }

    public OffsetDateTime getTriggeredAt() {
        return triggeredAt;
    }

    public BigDecimal getScoreContribution() {
        return scoreContribution;
    }

    @Override
    public boolean equals(Object o) {
        return this == o
                || (o instanceof RiskAssessment that && assessmentId != null && assessmentId.equals(that.assessmentId));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
