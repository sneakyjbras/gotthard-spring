package ch.gotthard.service;

import ch.gotthard.core.model.RiskLevel;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One row of {@code ai_analyses} as an operator reads it.
 *
 * <p><b>Two levels, side by side, on purpose.</b> {@link #computedLevel} is what the rules decided;
 * {@link #assessedLevel} is the model's own independent call; {@link #levelsDiverged} is
 * PostgreSQL's comparison of the two, read back from the generated column rather than recomputed
 * here — the database is the one that decided it, so the database is what this reports.
 *
 * <p>The provider, model, prompt version, token counts and latency travel with the answer because an
 * analysis is an audit record: six months on, "what wrote this, and under which prompt" is a
 * question that has to be answerable from the row itself.
 *
 * @param citations the policy the model was shown, best match first. Empty on a history listing —
 *     that endpoint answers "what has been asked about this customer", and a page of full policy
 *     text per entry answers a question nobody asked. {@code GET /api/analyses/{id}} fills it in.
 */
public record AiAnalysisView(
        UUID analysisId,
        CustomerView customer,
        OperatorRef requestedBy,
        OffsetDateTime createdAt,
        OffsetDateTime windowFrom,
        OffsetDateTime windowTo,
        BigDecimal computedScore,
        RiskLevel computedLevel,
        RiskLevel assessedLevel,
        boolean levelsDiverged,
        String summary,
        List<String> recommendations,
        String provider,
        String model,
        String promptVersion,
        Integer inputTokens,
        Integer outputTokens,
        Integer latencyMs,
        List<PolicyCitationView> citations) {

    public AiAnalysisView {
        recommendations = List.copyOf(recommendations);
        citations = List.copyOf(citations);
    }
}
