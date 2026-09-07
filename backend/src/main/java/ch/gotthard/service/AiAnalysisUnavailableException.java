package ch.gotthard.service;

/**
 * The model produced no verdict, so no analysis was written.
 *
 * <p>Distinct from a server fault, and mapped to a different status for that reason. A declined or
 * unreachable model is a temporary condition of one downstream dependency, not a bug in this
 * application: the rules' own verdict is unaffected, the operator can retry, and nothing was
 * recorded that would have to be undone.
 */
public class AiAnalysisUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AiAnalysisUnavailableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
