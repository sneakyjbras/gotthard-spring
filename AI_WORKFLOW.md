# The AI workflow

The brief asks candidates to build this using AI tools and agents, says the
assessment covers *how* it was built and what methodology was used, and asks for
a summary of the LLMs chosen and the agent instructions given.

This is that answer. `docs/ai-methodology.md` goes deeper; every agent prompt is
committed verbatim in `docs/agents/`.

## The shape of it

One Claude session acted as **supervisor**. It read the brief, designed the
schema, decided the interfaces, spawned agents, verified what came back and
performed every merge. It wrote no feature code.

Fifteen **subagents** did the implementation. Each owned one task, worked in its
own git worktree with a disjoint file scope, committed to its own branch, and
never merged. The supervisor merged.

That division is the whole method. An agent writing code and an agent deciding
whether that code is acceptable should not be the same agent.

## The models, and why each

**Claude Opus 5** for the supervisor throughout, and for three implementation
tasks. **Claude Sonnet 5** for the rest.

The rule: **Opus where a wrong decision propagates; Sonnet where the answer is
specified and the work is careful execution.**

| Task | Model | Reasoning |
|---|---|---|
| Risk core — the rule abstraction | **Opus** | Every rule written afterwards inherits its shape. A weak abstraction cannot be fixed later without touching everything downstream. |
| SQL window functions | **Opus** | Rolling `RANGE` windows fail *silently*. Wrong numbers would poison every risk score without raising anything. |
| AI analysis service | **Opus** | Prompt design decides the quality of the thing being demonstrated, and the structured-output contract has to hold for two adapters. |
| JPA mapping, authentication, frontend, graph search, retrieval, seed data, infrastructure | **Sonnet** | Specified before the agent started. The work was execution, and the constraints that mattered were fixed in `CLAUDE.md` first. |

The application's own analysis also calls **Claude Opus 5**, behind a port with a
deterministic stub adapter so the whole system runs with no credentials.

## How the work was ordered

Tasks were mapped to a dependency graph and executed in waves — independent work
in parallel, barriers serialised.

```
wave 0   foundation, schema, architectural tests        serial barrier
wave 1   domain · security · risk core · frontend       4 in parallel
wave 2   activity API · wallet graph · retrieval · seed 4 in parallel
wave 3   AI analysis · dashboard · analysis panel       3 in parallel
wave 4   images · chart · GitOps · observability        2 then 1
```

The git history preserves this: each agent's work is a branch merged with
`--no-ff`, so the topology shows what genuinely ran in parallel.

## What the supervisor actually checked

Merging was gated, not ceremonial. Every branch was verified for:

- the full suite passing, including the architectural rules
- changes staying inside the agent's declared file scope — **verified against the
  merge base**, not against `main`, which contains commits the agent never saw
- the *merged* tree building, not merely the branch
- commit authorship

That scope check earned its place. It caught a case where two agents had written
into one directory.

## What went wrong

A methodology write-up with no failures in it is marketing. These are real.

**Worktree isolation does not survive a resume.** When an agent was resumed after
an outage had pruned its worktree, it wrote into a sibling's. Two agents shared
one directory. It was recoverable only because their file scopes did not overlap,
so the work separated cleanly by path. Had they touched the same file, one would
have silently destroyed the other's work.

**Agents were interrupted by usage limits, repeatedly.** Because they committed to
their own branches and reported honestly, little was lost — but twice an agent had
written substantial code without committing, and recovering it meant working out
which files belonged to whom.

**A green exit code is not a green build.** Piping Gradle through `tail` reports
`tail`'s exit status; a failing build was briefly recorded as passing. Later, the
cluster bring-up script printed its success banner and exited zero while two
applications were still `Missing`. Both are now fixed, and the second one had to
be found by reading `kubectl` rather than trusting the script.

**Editing a shell script while it is executing breaks it.** Bash reads a script
incrementally from a byte offset, so an edit mid-run shifts everything under it.

## What the agents got right, unprompted

They reported honestly. One flagged that it had widened a test class's visibility
outside its scope, and explained why the alternative was a compile-time wall.
Another flagged adding a runtime dependency, because `Argon2PasswordEncoder`
throws without Bouncy Castle. Two flagged uncertainty about commit attribution
rather than silently choosing. Several stated plainly that they could not open a
browser and that the visual result was therefore unverified. None of this was
caught in review; all of it was volunteered.

They also found real bugs by running things rather than reasoning about them.
Hibernate treats `CHAR` and `VARCHAR` as distinct JDBC types. PostgreSQL refuses
to bind a `varchar` parameter into a `vector` column even when the value is null.
`"x".slice(-0)` returns the whole string in JavaScript, not an empty one.

## What this approach is good and bad at

**Good at parallel breadth.** Four agents building the domain layer, security,
the risk engine and the frontend simultaneously, each with a precise brief,
produced more working and tested code in an evening than serial work would have.

**Bad at anything needing shared context mid-flight.** Each agent starts cold and
re-derives the codebase before writing a line — a real cost, and the reason to
prefer fewer, better-scoped agents over more. Where two agents had to agree on
something — the shape of `Features`, the sentinel for "no path found" — it had to
be settled *before* they started, in the architecture, rather than negotiated
between them.

Which is the actual lesson. The supervisor's job turned out not to be
supervision. It was deciding what the interfaces were, before anyone wrote code
against them.
