package ch.gotthard.domain.model;

/**
 * Mirrors the {@code CHECK (status IN ('COMPLETED','PENDING','FAILED','REVERSED'))} constraint on
 * {@code transactions.status}.
 */
public enum TransactionStatus {
    COMPLETED,
    PENDING,
    FAILED,
    REVERSED
}
