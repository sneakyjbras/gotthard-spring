package ch.gotthard.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * Which policy chunk was shown to the model for a given analysis, and how well it matched. Maps
 * {@code ai_analysis_citations}, a pure link table with a composite primary key
 * {@code (analysis_id, chunk_id)} — see {@link AiAnalysisCitationId}.
 *
 * <p>Unidirectional from here: neither {@link AiAnalysis} nor {@link PolicyChunk} carries a
 * back-reference collection, since nothing needs to navigate "all citations of this analysis" as an
 * in-memory graph rather than a query. An audit row, never updated after insert — no setters.
 */
@Entity
@Table(name = "ai_analysis_citations")
public class AiAnalysisCitation {

    @EmbeddedId
    private AiAnalysisCitationId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("analysisId")
    @JoinColumn(name = "analysis_id")
    private AiAnalysis analysis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("chunkId")
    @JoinColumn(name = "chunk_id")
    private PolicyChunk chunk;

    @Column(nullable = false)
    private float similarity;

    @Column(nullable = false)
    private short rank;

    protected AiAnalysisCitation() {
        // JPA
    }

    public AiAnalysisCitation(AiAnalysis analysis, PolicyChunk chunk, float similarity, short rank) {
        this.id = new AiAnalysisCitationId(analysis.getAnalysisId(), chunk.getChunkId());
        this.analysis = analysis;
        this.chunk = chunk;
        this.similarity = similarity;
        this.rank = rank;
    }

    public AiAnalysisCitationId getId() {
        return id;
    }

    public AiAnalysis getAnalysis() {
        return analysis;
    }

    public PolicyChunk getChunk() {
        return chunk;
    }

    public float getSimilarity() {
        return similarity;
    }

    public short getRank() {
        return rank;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof AiAnalysisCitation that && id != null && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
