package ch.gotthard.service;

import java.util.UUID;

/** No analysis answers to that id. */
public class AiAnalysisNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AiAnalysisNotFoundException(final UUID analysisId) {
        super("No AI analysis with id " + analysisId);
    }
}
