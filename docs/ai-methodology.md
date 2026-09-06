# How this was built

The brief asks for a summary of the LLMs used and of the instructions given to
them. This is that summary, written as the work happened rather than
reconstructed afterwards. Every prompt quoted here is in `docs/agents/`, recorded
at the moment the agent was spawned.

## The model

**Claude Opus 5** (`claude-opus-5`) throughout, in two roles.

One session acted as **supervisor**: it planned, wrote the schema, spawned
agents, verified their branches and performed every merge. It never wrote feature
code. Subagents did the implementation, each in an isolated git worktree with a
disjoint file scope, on **Claude Opus 5** or **Claude Sonnet 5** depending on the
task.

The application's own AI analysis also calls Claude Opus 5, behind a port with a
deterministic stub adapter so the application runs with no API key at all.

## Choosing a model per task

The rule was simple: **Opus where a wrong decision propagates, Sonnet where the
answer is specified and the work is careful execution.**

| Task | Model | Why |
|---|---|---|
| Risk core — rules, scoring | **Opus** | The rule abstraction is inherited by every rule written after it. A weak one cannot be fixed later without touching everything downstream. |
| Activity API + SQL windows | **Opus** | Rolling `RANGE` windows fail silently. Wrong numbers would have poisoned every risk score without erroring. |
| JPA entities and repositories | Sonnet | Schema fully specified; joined-table inheritance is a known pattern. |
| Operator authentication | Sonnet | Standard Spring Security. The one decision that mattered — argon2id, not encryption — was fixed in advance by `CLAUDE.md`. |
| Frontend scaffold | Sonnet | The design system was specified before the agent started; it implemented rather than invented. |
| Wallet graph traversal | Sonnet | BFS is a known algorithm and the modelling was settled in the architecture. |
| Policy corpus and retrieval | Sonnet | Prose to a specification, plus port/adapter plumbing. |
| Risk rules and seed data | Sonnet | The shape was specified; the value was in the narratives being believable. |

## Orchestration

Work was mapped to a dependency graph and executed in waves. Independent tasks
ran in parallel; anything that would conflict with everything else — the schema,
the package layout, the architectural tests — was a barrier the supervisor did
serially first.

```
wave 0  foundation, schema, ArchUnit          (serial barrier)
wave 1  domain · security · risk core · frontend   (4 parallel)
wave 2  activity API · BFS · retrieval · seed data (4 parallel)
wave 3  AI analysis · dashboard                    (2 parallel)
wave 4  analysis history · documentation · demo
```

The git history preserves this. Each agent's work is a branch merged with
`--no-ff`, so the topology shows what ran in parallel and what did not.

Agents committed to their own branch and never merged. The supervisor verified
each one — scope, author identity, the full test suite, the architectural rules —
and merged only after the *merged* tree was checked, not merely the branch.

## What agents got right that is worth recording

They reported honestly. The domain agent flagged that it had made a test class
`public` outside its scope and explained why the alternative was a compile-time
wall. The security agent flagged adding Bouncy Castle and explained that
`Argon2PasswordEncoder` throws without it. Two agents flagged uncertainty about
commit attribution rather than silently picking. None of these were caught by
review; all were volunteered.

They also found real bugs by running things rather than assuming. Hibernate
treats `CHAR` and `VARCHAR` as distinct JDBC types. PostgreSQL rejects binding a
`varchar` parameter into a `vector` column even when the value is null. A
`truncateMiddle(id, 8, 0)` helper rendered garbage because `"x".slice(-0)`
returns the whole string in JavaScript. Each was found by a test or a screenshot,
not by reasoning.

## What went wrong

**Worktree isolation is not preserved across a resume.** When an agent was
resumed after an outage had pruned its worktree, it did not get a fresh one — it
wrote into a sibling agent's tree. Two agents shared one directory for a while.
It was recoverable only because their file scopes did not overlap, so the work
separated cleanly by path. Had they both touched the same file, one would have
silently destroyed the other's work.

The mitigation used here was to give every agent a disjoint scope *and* to check,
before every merge, that the diff against the merge base contained nothing
outside that scope. That check caught the contamination. Scope discipline turned
out to matter for a reason beyond tidiness.

**Usage limits interrupt long agent runs.** Several agents were killed mid-task.
Because they committed to their own branches and reported honestly, no work was
lost — but two had written substantial code without committing, and recovering it
meant reconstructing which files belonged to whom.

**A green exit code is not a green build.** Piping Gradle through `tail` reports
`tail`'s exit status. A failing build was briefly recorded as passing. Build
verification now never pipes.

## What this approach is good and bad at

It is good at parallel breadth. Four agents building the domain layer, the
security layer, the risk engine and the frontend simultaneously, each with a
precise brief, produced more working and tested code in an evening than serial
work would have.

It is bad at anything requiring shared context mid-flight. Each agent starts cold
and re-derives the codebase before writing a line, which is a real cost and the
reason to prefer fewer, better-scoped agents over more. Where two agents needed
to agree on something — the shape of `Features`, the sentinel for "no path found"
— it had to be settled *before* they started, in the architecture, not negotiated
between them.

The supervisor's job is therefore mostly not supervision. It is deciding what the
interfaces are, before anyone writes code against them.
