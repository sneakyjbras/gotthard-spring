package ch.gotthard.api;

import ch.gotthard.service.ActivityWindow;
import ch.gotthard.service.AiAnalysisService;
import ch.gotthard.service.AiAnalysisView;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Running an AI analysis, and reading the ones already run.
 *
 * <p>Three thin methods over one use case, and no {@code domain} import anywhere — {@code
 * ArchitectureTest} forbids it, and there is nothing here that would want one.
 *
 * <p><b>The operator is taken from the session, never from the request.</b> {@link Principal} is
 * what the filter chain already established; a {@code requestedBy} field in the body would be a
 * field a client could put anyone's name in, and this row is an audit record of who asked.
 *
 * <p>Every route requires an authenticated session, and none of them says so: {@link
 * ch.gotthard.security.SecurityConfig} authenticates everything not explicitly permitted, so a route
 * added here is closed by default rather than by anyone remembering.
 */
@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final AiAnalysisService analyses;

    public AnalysisController(final AiAnalysisService analyses) {
        this.analyses = analyses;
    }

    /**
     * Runs one analysis over the window and records it.
     *
     * <p>A POST because it is not idempotent by design: each call is a separate question asked at a
     * separate moment, and the history of who asked what, when, is the point of keeping them.
     */
    @PostMapping("/customers/{customerId}/analysis")
    public AiAnalysisView analyse(
            @PathVariable final UUID customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    final Optional<OffsetDateTime> from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    final Optional<OffsetDateTime> to,
            final Principal operator) {
        return analyses.analyse(customerId, ActivityWindow.between(from, to), operator.getName());
    }

    /** Every analysis ever run on this customer, newest first. */
    @GetMapping("/customers/{customerId}/analyses")
    public List<AiAnalysisView> history(@PathVariable final UUID customerId) {
        return analyses.history(customerId);
    }

    /** One analysis, with the policy chunks the model was shown. */
    @GetMapping("/analyses/{analysisId}")
    public AiAnalysisView analysis(@PathVariable final UUID analysisId) {
        return analyses.find(analysisId);
    }
}
