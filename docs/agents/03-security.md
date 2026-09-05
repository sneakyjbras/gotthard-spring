# Agent 03 — Operator authentication

**Model:** Claude Sonnet 5 · **Wave:** 1 · **Scope:** `backend/src/main/java/ch/gotthard/security/**`

Sonnet because this is standard Spring Security wiring. The one part that genuinely
matters — the hashing choice — is fixed by `CLAUDE.md` and verified at merge.

## Instructions given

> Session-based login for operators, hashed with argon2id into `operator_credentials`.
> Hashed, never encrypted: encryption is reversible and this is a bank. Credentials stay
> in their own table so operator records can be read without loading a hash. Session
> cookie must be HttpOnly and SameSite. Seed at least two operators with different roles
> so "login by different operators" is demonstrable.
