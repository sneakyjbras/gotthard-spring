# Agent 01 — Risk core

**Model:** Claude Opus 5 · **Wave:** 1 · **Scope:** `backend/src/main/java/ch/gotthard/core/**`

Opus because this is the architectural heart. The rule abstraction chosen here is
inherited by every rule written afterwards; a weak abstraction cannot be fixed later
without touching everything downstream.

## Instructions given

> Build the pure-Java risk core for gotthard-spring. Read `CLAUDE.md` first — it is
> binding, especially the pyramid rules and the code style.
>
> Your scope is `backend/src/main/java/ch/gotthard/core/**` and its tests. Touch
> nothing else. `core` must import nothing from Spring, JPA, Hibernate, SQL or HTTP —
> `ArchitectureTest` enforces this and must stay green.
>
> Deliver three things:
>
> 1. `core/model` — the value objects the rest of the system speaks in: `Money`,
>    `ActivityType`, `RiskLevel` (LOW/MEDIUM/HIGH/CRITICAL), `RiskScore`, and a
>    `Features` record carrying the precomputed signals a rule reads (rolling sums,
>    near-threshold counts, counterparty corridors, declines, hop distance). Records,
>    immutable, no nulls.
>
> 2. `core/risk` — the rule abstraction and scoring. A `Rule` is a tiny self-contained
>    component: it knows its `rule_code`, which activity types it applies to, whether it
>    fires for a given `Features`, and what it contributes. Adding a rule must never
>    require editing the scorer. Weights are supplied from outside (they live in the
>    `risk_rules` table) — the core must not hardcode them. The scorer sums
>    contributions and quantizes to a `RiskLevel`; make the band thresholds explicit and
>    testable, not magic numbers scattered about.
>
> 3. Write at least six real rules covering all three activity types, each its own class
>    with its own test. Look at `V1__baseline.sql` for the fields available. Aim for
>    rules a compliance officer would recognise: near-threshold structuring, cross-border
>    corridors to elevated-risk jurisdictions, card-not-present decline clusters, rapid
>    crypto movement to exchanges, dormancy followed by a burst.
>
> Style: stream pipelines over accumulating loops; lambdas scoped to their routine;
> records; `final` by default; no Lombok. Small methods — a routine orchestrating named
> subroutines.
>
> Tests boot nothing. No Spring context, no database, no Testcontainers. If a core test
> needs either, your design is wrong. JUnit 5 + AssertJ, `given_when_then` names.
>
> Run `./gradlew spotlessApply check` before reporting. Commit to your own branch. Do
> not merge. Report honestly — if something does not pass, say so plainly rather than
> describing what you intended.
