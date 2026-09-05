package ch.gotthard.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One retrievable clause of policy text, with its embedding. Maps {@code policy_chunks}, the
 * knowledge base behind RAG.
 *
 * <p>{@link #embedding} is a pgvector {@code vector(384)} column. Retrieval (similarity search,
 * writing real embeddings) is owned by {@code ai/}; this mapping only needs to satisfy Hibernate
 * schema validation, so it is a plain {@code String} behind an explicit {@code columnDefinition} —
 * enough to validate, not enough to compute with. It is mapped {@code insertable = false, updatable
 * = false} on purpose: PostgreSQL rejects binding a {@code varchar}-typed parameter into a {@code
 * vector} column outright (even when the value is {@code null} — the parameter's declared type is
 * checked independently of the value), so a plain String can read this column but must never try to
 * write it. Whoever builds the retriever will need a real vector-aware type to write embeddings, and
 * should widen this mapping accordingly rather than relax these flags.
 *
 * <p>{@link #metadata} is JSONB, mapped as raw JSON text via Hibernate's native {@code
 * SqlTypes.JSON} support rather than a structured type, for the same reason: nothing downstream of
 * this table needs it parsed yet.
 */
@Entity
@Table(name = "policy_chunks")
public class PolicyChunk {

    @Id
    @Column(name = "chunk_id")
    private UUID chunkId;

    @Column(nullable = false, length = 32)
    private String document;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 64)
    private String section;

    @Column(nullable = false)
    private String body;

    // Owned by ai/ (retrieval). Placeholder, read-only mapping only — see class Javadoc.
    @Column(columnDefinition = "vector(384)", insertable = false, updatable = false)
    private String embedding;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String metadata;

    protected PolicyChunk() {
        // JPA
    }

    public PolicyChunk(UUID chunkId, String document, String title, String section, String body, String metadata) {
        this.chunkId = chunkId;
        this.document = document;
        this.title = title;
        this.section = section;
        this.body = body;
        this.metadata = metadata;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    public String getDocument() {
        return document;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    /** Read-only through this mapping — see the class Javadoc for why there is no setter. */
    public String getEmbedding() {
        return embedding;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof PolicyChunk that && chunkId != null && chunkId.equals(that.chunkId));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
