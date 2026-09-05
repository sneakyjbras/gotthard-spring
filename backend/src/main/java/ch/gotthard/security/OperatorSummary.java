package ch.gotthard.security;

import ch.gotthard.domain.model.Operator;
import ch.gotthard.domain.model.OperatorRole;
import java.util.UUID;

/**
 * The wire shape of an authenticated operator: everything {@code POST /api/auth/login} and
 * {@code GET /api/auth/me} return, and nothing from {@code operator_credentials} — a password hash
 * never crosses into this type.
 */
public record OperatorSummary(UUID operatorId, String username, String displayName, OperatorRole role) {

    static OperatorSummary from(Operator operator) {
        return new OperatorSummary(
                operator.getOperatorId(), operator.getUsername(), operator.getDisplayName(), operator.getRole());
    }
}
