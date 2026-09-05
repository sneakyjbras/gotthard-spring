package ch.gotthard.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One AI-written assessment of a customer's activity, requested by an operator. Maps {@code
 * ai_analyses}.
 *
 * <p>{@link #computedLevel} is what our deterministic rules decided; {@link #assessedLevel} is the
 * model's own independent call. They are stored apart on purpose — the AI never decides risk, it
 * only explains and proposes actions, and disagreement between the two is itself a signal worth
 * surfacing, not an error to hide.
 *
 * <p>{@link #levelsDiverged} is a PostgreSQL {@code GENERATED ALWAYS ... STORED} column
 * ({@code computed_level <> assessed_level}). It is mapped {@code insertable = false, updatable =
 * false}: Hibernate must never attempt to write it, only read what PostgreSQL computed.
 *
 * <p>A snapshot of exactly what was analysed and concluded — like {@link RiskAssessment}, this is an
 * audit record and is never updated after insert, so it exposes no setters.
 */
@Entity
@Table(name = "ai_analyses")
public class AiAnalysis {

    @Id
    @Column(name = "analysis_id")
    private UUID analysisId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private Operator requestedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "window_from", nullable = false)
    private OffsetDateTime windowFrom;

    @Column(name = "window_to", nullable = false)
    private OffsetDateTime windowTo;

    @Column(name = "computed_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal computedScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "computed_level", nullable = false, length = 8)
    private RiskLevel computedLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessed_level", nullable = false, length = 8)
    private RiskLevel assessedLevel;

    @Column(name = "levels_diverged", insertable = false, updatable = false, nullable = false)
    private boolean levelsDiverged;

    @Column(nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String recommendations;

    @Column(nullable = false, length = 16)
    private String provider;

    @Column(nullable = false, length = 64)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 16)
    private String promptVersion;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    protected AiAnalysis() {
        // JPA
    }

    public AiAnalysis(
            UUID analysisId,
            Customer customer,
            Operator requestedBy,
            OffsetDateTime createdAt,
            OffsetDateTime windowFrom,
            OffsetDateTime windowTo,
            BigDecimal computedScore,
            RiskLevel computedLevel,
            RiskLevel assessedLevel,
            String summary,
            String recommendations,
            String provider,
            String model,
            String promptVersion,
            Integer inputTokens,
            Integer outputTokens,
            Integer latencyMs,
            String rawResponse) {
        this.analysisId = analysisId;
        this.customer = customer;
        this.requestedBy = requestedBy;
        this.createdAt = createdAt;
        this.windowFrom = windowFrom;
        this.windowTo = windowTo;
        this.computedScore = computedScore;
        this.computedLevel = computedLevel;
        this.assessedLevel = assessedLevel;
        this.summary = summary;
        this.recommendations = recommendations;
        this.provider = provider;
        this.model = model;
        this.promptVersion = promptVersion;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.rawResponse = rawResponse;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Operator getRequestedBy() {
        return requestedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getWindowFrom() {
        return windowFrom;
    }

    public OffsetDateTime getWindowTo() {
        return windowTo;
    }

    public BigDecimal getComputedScore() {
        return computedScore;
    }

    public RiskLevel getComputedLevel() {
        return computedLevel;
    }

    public RiskLevel getAssessedLevel() {
        return assessedLevel;
    }

    /** Computed by PostgreSQL, not by us — see the class Javadoc. */
    public boolean isLevelsDiverged() {
        return levelsDiverged;
    }

    public String getSummary() {
        return summary;
    }

    public String getRecommendations() {
        return recommendations;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof AiAnalysis that && analysisId != null && analysisId.equals(that.analysisId));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
