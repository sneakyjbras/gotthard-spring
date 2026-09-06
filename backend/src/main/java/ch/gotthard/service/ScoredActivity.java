package ch.gotthard.service;

import ch.gotthard.core.model.ActivityType;
import ch.gotthard.core.risk.RiskEvaluation;
import ch.gotthard.domain.query.FeatureRow;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One transaction and what the rules made of it — the row that was scored kept beside the score, so
 * the audit trail and the report can both be written without going back to the database for the
 * transaction's own details.
 */
record ScoredActivity(FeatureRow row, RiskEvaluation evaluation) {

    UUID transactionId() {
        return row.activity().transactionId();
    }

    OffsetDateTime occurredAt() {
        return row.activity().occurredAt();
    }

    ActivityType channel() {
        return ActivityType.valueOf(row.activity().activityType());
    }

    boolean fired() {
        return !evaluation.hits().isEmpty();
    }
}
