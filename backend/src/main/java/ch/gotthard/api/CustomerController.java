package ch.gotthard.api;

import ch.gotthard.service.ActivityOverview;
import ch.gotthard.service.ActivityOverviewService;
import ch.gotthard.service.ActivityWindow;
import ch.gotthard.service.CustomerRiskReport;
import ch.gotthard.service.CustomerSearchService;
import ch.gotthard.service.CustomerView;
import ch.gotthard.service.RiskEvaluationService;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Everything an operator does with one customer: find them, see what they have been doing, and ask
 * what the rules make of it.
 *
 * <p>Three thin methods. Each parses what arrived off the wire, hands it to a use case, and returns
 * what came back — no branching, no assembling, and in particular no repositories: {@code
 * ArchitectureTest} forbids this package from importing {@code domain} at all, and the reason is
 * visible here as three methods that are each two lines long.
 *
 * <p>Every route requires an authenticated session. That is not stated in this file because it is
 * not decided in this file — {@link ch.gotthard.security.SecurityConfig} authenticates every request
 * that is not explicitly permitted, so a new endpoint is protected by default rather than by
 * remembering to protect it.
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerSearchService customerSearch;
    private final ActivityOverviewService activityOverview;
    private final RiskEvaluationService riskEvaluation;

    public CustomerController(
            final CustomerSearchService customerSearch,
            final ActivityOverviewService activityOverview,
            final RiskEvaluationService riskEvaluation) {
        this.customerSearch = customerSearch;
        this.activityOverview = activityOverview;
        this.riskEvaluation = riskEvaluation;
    }

    /** Search by whichever identifier the operator has: the UUID, or the printed reference. */
    @GetMapping("/{idOrReference}")
    public CustomerView customer(@PathVariable final String idOrReference) {
        return customerSearch.find(idOrReference);
    }

    /** The activity screen. Both ends of the window are optional; {@link ActivityWindow} fills them in. */
    @GetMapping("/{customerId}/activity")
    public ActivityOverview activity(
            @PathVariable final UUID customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    final Optional<OffsetDateTime> from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    final Optional<OffsetDateTime> to) {
        return activityOverview.overview(customerId, ActivityWindow.between(from, to));
    }

    /**
     * Runs the rules over the window and records what fired.
     *
     * <p>A GET that writes looks wrong and is not: the audit trail is part of what evaluating means,
     * and the write is idempotent per transaction and rule, so this route is safe to repeat — see
     * {@link RiskEvaluationService}.
     */
    @GetMapping("/{customerId}/risk")
    public CustomerRiskReport risk(
            @PathVariable final UUID customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    final Optional<OffsetDateTime> from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    final Optional<OffsetDateTime> to) {
        return riskEvaluation.evaluate(customerId, ActivityWindow.between(from, to));
    }
}
