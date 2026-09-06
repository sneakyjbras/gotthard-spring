package ch.gotthard.service;

/**
 * No customer answers to that identifier.
 *
 * <p>A use case's way of saying so without knowing what an HTTP status is — the web layer decides
 * that, in one place, and nothing here has to import a status code to report a missing row.
 */
public class CustomerNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CustomerNotFoundException(final String idOrReference) {
        super("No customer with id or reference " + idOrReference);
    }
}
