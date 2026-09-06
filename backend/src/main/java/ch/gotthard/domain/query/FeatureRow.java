package ch.gotthard.domain.query;

/**
 * One transaction's worth of precomputed signals, straight off the window query.
 *
 * <p>Deliberately shaped like {@code core.model.Features} without being it: the same five parts, but
 * made of {@code String}, {@code long} and {@code BigDecimal} — whatever the columns actually
 * contained. Nothing here validates, converts or decides; that is the assembler's work, and keeping
 * it out of this package is what stops SQL vocabulary leaking into the risk core.
 *
 * <p>A transaction belongs to one channel, so the other two channels' rows are present and empty.
 */
public record FeatureRow(
        ActivityRow activity, VelocityRow velocity, CardRow card, PaymentRow payment, CryptoRow crypto) {}
