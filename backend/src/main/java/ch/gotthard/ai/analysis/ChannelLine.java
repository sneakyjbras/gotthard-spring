package ch.gotthard.ai.analysis;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One line of the compact activity rendering: what a single channel did across the analysed window.
 *
 * <p>A channel summary rather than a transaction list, on purpose. Thirty days of a busy customer is
 * hundreds of rows, nearly all of them noise, and pasting them in would bury the handful of
 * transactions the rules actually objected to — those arrive separately, as {@link FiredRule}s. The
 * shape of the window is six numbers per channel; the evidence is the rules.
 */
public record ChannelLine(
        String channel,
        int transactionCount,
        String currency,
        BigDecimal volume,
        int unsuccessfulCount,
        OffsetDateTime firstAt,
        OffsetDateTime lastAt) {}
