package ch.gotthard.domain.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@link PolicyChunkRepositoryCustom} via {@link NamedParameterJdbcTemplate} rather than a Spring Data
 * derived query or an {@code @Query} method: pgvector's nearest-neighbour operator ({@code <=>}) has
 * no JPQL equivalent, and the query vector has to reach PostgreSQL as a {@code vector}-typed value.
 *
 * <p>The query vector is bound as an ordinary text parameter and cast explicitly in the SQL itself
 * ({@code cast(:queryVector as vector)}) rather than through any vector-aware JDBC binder. That sidesteps
 * exactly the JDBC type mismatch {@link ch.gotthard.domain.model.PolicyChunk}'s own Javadoc describes
 * for writing the column: casting a plain, untyped text parameter is always legal PostgreSQL syntax, so
 * there is nothing driver- or ORM-specific for this query to depend on — it would work unchanged even
 * without {@code hibernate-vector} on the classpath, which is a property worth keeping for a query this
 * central. Rows are mapped by hand rather than through a Spring Data projection interface for the same
 * reason: total control over the mapping, and one thing fewer to trust blindly around a type PostgreSQL
 * only just barely lets a plain JDBC driver see as text.
 */
@Repository
class PolicyChunkRepositoryImpl implements PolicyChunkRepositoryCustom {

    private static final String FIND_NEAREST_SQL =
            """
            select chunk_id, document, title, section, body,
                   1 - (embedding <=> cast(:queryVector as vector)) as similarity
            from policy_chunks
            where embedding is not null
            order by embedding <=> cast(:queryVector as vector)
            limit :limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    PolicyChunkRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<PolicyChunkMatch> findNearest(float[] queryVector, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queryVector", toVectorLiteral(queryVector))
                .addValue("limit", limit);
        return jdbcTemplate.query(FIND_NEAREST_SQL, params, PolicyChunkRepositoryImpl::toMatch);
    }

    /** pgvector's text input format, {@code [v1,v2,...]}, fixed-point so every element is unambiguous. */
    private static String toVectorLiteral(float[] vector) {
        return IntStream.range(0, vector.length)
                .mapToObj(i -> String.format(Locale.ROOT, "%.8f", vector[i]))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static PolicyChunkMatch toMatch(ResultSet row, int rowNum) throws SQLException {
        return new PolicyChunkMatch(
                row.getObject("chunk_id", UUID.class),
                row.getString("document"),
                row.getString("title"),
                row.getString("section"),
                row.getString("body"),
                row.getDouble("similarity"));
    }
}
