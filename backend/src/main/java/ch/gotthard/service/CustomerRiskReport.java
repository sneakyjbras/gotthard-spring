package ch.gotthard.service;

import ch.gotthard.core.model.RiskLevel;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * What the rules made of a customer's window.
 *
 * <p>The headline score is the <b>highest</b> any single transaction scored, not a sum across the
 * window. A customer is as risky as their riskiest activity: summing would make a long, dull history
 * look worse than one deliberate structuring run, and would climb every time the window widened —
 * which would make the number an artefact of the query rather than a statement about the customer.
 *
 * @param findings every rule that fired, on every transaction, newest activity first
 */
public record CustomerRiskReport(
        CustomerView customer,
        OffsetDateTime from,
        OffsetDateTime to,
        int transactionsEvaluated,
        BigDecimal score,
        RiskLevel level,
        List<RiskFinding> findings) {

    public CustomerRiskReport {
        findings = List.copyOf(findings);
    }
}
