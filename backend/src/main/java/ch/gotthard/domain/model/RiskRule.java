package ch.gotthard.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The tunable half of a risk rule. Maps {@code risk_rules}. The logic itself is a Java class in
 * {@code core/}, bound to its row here by {@link #ruleCode} (e.g. {@code R-04}); this row only
 * carries what changes without a redeploy — {@link #weight} and {@link #enabled} both have setters
 * for exactly that reason. {@link #thresholdLogic} is the human-readable statement of the condition,
 * kept in step with the Java by convention, not by the type system.
 */
@Entity
@Table(name = "risk_rules")
public class RiskRule {

    @Id
    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "rule_code", nullable = false, unique = true, length = 16)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, length = 128)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false, length = 8)
    private RuleScope appliesTo;

    @Column(name = "threshold_logic", nullable = false)
    private String thresholdLogic;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(nullable = false)
    private boolean enabled;

    protected RiskRule() {
        // JPA
    }

    public RiskRule(
            UUID ruleId,
            String ruleCode,
            String ruleName,
            RuleScope appliesTo,
            String thresholdLogic,
            BigDecimal weight,
            boolean enabled) {
        this.ruleId = ruleId;
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.appliesTo = appliesTo;
        this.thresholdLogic = thresholdLogic;
        this.weight = weight;
        this.enabled = enabled;
    }

    public UUID getRuleId() {
        return ruleId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public RuleScope getAppliesTo() {
        return appliesTo;
    }

    public String getThresholdLogic() {
        return thresholdLogic;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof RiskRule that && ruleId != null && ruleId.equals(that.ruleId));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
