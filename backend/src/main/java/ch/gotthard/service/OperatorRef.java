package ch.gotthard.service;

import java.util.UUID;

/**
 * The operator an analysis is attributed to, as the console shows them.
 *
 * <p>Three fields, no role and no credentials: a history list says who asked and when, and needs
 * nothing else to say it. {@code security.OperatorSummary} is a different shape for a different
 * question — who is logged in — and coupling the two would make one of them wrong the first time
 * either question changed.
 */
public record OperatorRef(UUID operatorId, String username, String displayName) {}
