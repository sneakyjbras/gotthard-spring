package ch.gotthard.api;

/**
 * The one error shape this API speaks.
 *
 * <p>Deliberately the same single-field body {@link ch.gotthard.security.SecurityConfig} already
 * returns for 401 and 403, so a client has one thing to parse whether the request was refused by the
 * filter chain or by a controller.
 */
public record ApiError(String error) {}
