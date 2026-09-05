package ch.gotthard.security;

import jakarta.validation.constraints.NotBlank;

/** The credentials {@code POST /api/auth/login} accepts. */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
