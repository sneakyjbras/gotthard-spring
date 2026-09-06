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
 * <p>{@link #embedding} is a pgvector {@code vector(384)} column, mapped as {@code float[]} via
 * {@code SqlTypes.VECTOR_FLOAT32} — Hibernate's own vector-aware JDBC type, contributed by the {@code
 * hibernate-vector} module (see {@code build.gradle.kts}), not a hand-rolled one. This used to be a
 * plain {@code String} behind an explicit {@code columnDefinition}, mapped {@code insertable = false,
 * updatable = false}: enough to satisfy Hibernate schema validation, never enough to write with,
 * because PostgreSQL rejects binding a {@code varchar}-typed parameter into a {@code vector} column
 * outright (even when the value is {@code null} — the parameter's declared type is checked
 * independently of the value). {@code ai.retrieval} widens the mapping to a real vector-aware type
 * instead of relaxing those flags on the old one, which is what actually fixes the problem: {@link
 * ch.gotthard.ai.retrieval.PolicyCorpusEmbeddingInitializer} writes real embeddings back through this
 * entity via a plain {@code repository.save(chunk)}, no workaround required. The similarity *search*
 * itself is a different concern — a ranked, {@code LIMIT}-ed nearest-neighbour query has no JPQL
 * equivalent — and is handled separately in {@code PolicyChunkRepositoryImpl} via a native query; see
 * its Javadoc.
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

    @JdbcTypeCode(SqlTypes.VECTOR_FLOAT32)
    @Column(columnDefinition = "vector(384)")
    private float[] embedding;

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

    public float[] getEmbedding() {
        return embedding;
    }

    /** Owned by {@code ai.retrieval}: {@code PolicyCorpusEmbeddingInitializer} is the one caller. */
    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
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
