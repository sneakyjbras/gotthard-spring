package ch.gotthard.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite primary key of {@link AiAnalysisCitation}: one row per (analysis, chunk) pair. */
@Embeddable
public class AiAnalysisCitationId implements Serializable {

    @Column(name = "analysis_id")
    private UUID analysisId;

    @Column(name = "chunk_id")
    private UUID chunkId;

    protected AiAnalysisCitationId() {
        // JPA
    }

    public AiAnalysisCitationId(UUID analysisId, UUID chunkId) {
        this.analysisId = analysisId;
        this.chunkId = chunkId;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    @Override
    public boolean equals(Object o) {
        return this == o
                || (o instanceof AiAnalysisCitationId that
                        && Objects.equals(analysisId, that.analysisId)
                        && Objects.equals(chunkId, that.chunkId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(analysisId, chunkId);
    }
}
