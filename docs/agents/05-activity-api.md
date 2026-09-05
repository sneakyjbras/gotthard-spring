# Agent 05 — Activity query API and SQL window functions

**Model:** Opus · **Wave:** 2 · **Scope:** `service/`, `api/`, `domain/query/`

Opus because rolling `RANGE` windows are easy to get subtly wrong, and wrong numbers
would silently poison every risk score downstream.

## Instructions given
> Customer search by UUID or reference, an activity overview, and the SQL window
> functions that compute the `Features` the risk core consumes — rolling sums, 24h
> counts, near-threshold counts, dormancy gaps, cross-border corridors. Postgres does
> the set-based work; Java decides. Verified against Testcontainers with fixtures whose
> expected values are worked out by hand, not read back from the query.
