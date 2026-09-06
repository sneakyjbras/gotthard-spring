package ch.gotthard.domain.query;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Which rules are already on the audit trail for a set of transactions.
 *
 * <p>{@code risk_assessments} is written once per rule per transaction and never edited, so
 * re-evaluating a customer must add what is new rather than duplicate what is already recorded.
 * Reading the pairs back first is what lets the evaluation endpoint be called twice without the
 * trail growing a second copy of the same finding.
 */
@Repository
public class RecordedAssessmentQuery {

    private static final String SQL =
            """
            SELECT transaction_id, rule_id
            FROM risk_assessments
            WHERE transaction_id IN (:transactionIds)""";

    private final NamedParameterJdbcTemplate jdbc;

    public RecordedAssessmentQuery(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Rule ids already recorded, by transaction. Transactions with no findings are simply absent. */
    public Map<UUID, Set<UUID>> findRecordedRuleIds(final Collection<UUID> transactionIds) {
        return transactionIds.isEmpty() ? Map.of() : groupByTransaction(select(transactionIds));
    }

    private List<RecordedAssessment> select(final Collection<UUID> transactionIds) {
        return jdbc.query(
                SQL,
                new MapSqlParameterSource("transactionIds", transactionIds),
                (rs, rowNumber) -> new RecordedAssessment(
                        rs.getObject("transaction_id", UUID.class), rs.getObject("rule_id", UUID.class)));
    }

    private static Map<UUID, Set<UUID>> groupByTransaction(final List<RecordedAssessment> recorded) {
        return recorded.stream()
                .collect(Collectors.groupingBy(
                        RecordedAssessment::transactionId,
                        Collectors.mapping(RecordedAssessment::ruleId, Collectors.toUnmodifiableSet())));
    }

    /** A row of the audit trail, reduced to the pair that makes it unique. */
    private record RecordedAssessment(UUID transactionId, UUID ruleId) {}
}
