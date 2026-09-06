package ch.gotthard.service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A customer as every use case hands them out: identity and nothing else.
 *
 * <p>The entity stays behind the service boundary. The web layer is forbidden from importing {@code
 * domain} at all — {@code ArchitectureTest} enforces it — so this is the shape a customer has by the
 * time anything can serialise one.
 */
public record CustomerView(
        UUID customerId,
        String reference,
        String fullName,
        String country,
        String segment,
        OffsetDateTime onboardedAt) {}
