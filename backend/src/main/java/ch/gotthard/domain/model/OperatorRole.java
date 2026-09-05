package ch.gotthard.domain.model;

/** Mirrors the {@code CHECK (role IN ('OPERATOR','SUPERVISOR'))} constraint on {@code operators.role}. */
public enum OperatorRole {
    OPERATOR,
    SUPERVISOR
}
