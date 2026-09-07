package ch.gotthard.ai.analysis;

/**
 * Who the analysis is about, in the four attributes that change how a compliance analyst reads the
 * same behaviour: a corporate customer in a high-risk corridor is not a retail customer buying
 * groceries.
 *
 * <p>Deliberately no {@code customerId}. An opaque UUID tells the model nothing, and the identifiers
 * that do mean something — the printed reference and the name — are already here.
 */
public record AnalysedCustomer(String reference, String fullName, String country, String segment) {}
