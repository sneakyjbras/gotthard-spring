# gotthard-spring

Customer activity analytics for financial-services operators. Take-home for
Swissquote. The global directives in `~/.claude/CLAUDE.md` apply in full —
especially §0 (emergent complexity, the iterative method) and §3 (pipelines,
scoped lambdas). What follows is only what is specific to this repository.

**This project is graded twice: the application, and the methodology used to
build it.** Every agent prompt is written to `docs/agents/NN-task.md` at spawn
time. That file is a deliverable, not a byproduct.

## Effort split — revised, and the 20 has been cut

The original split reserved twenty per cent for four differentiators. Budget
pressure forced that to be revisited, and revisiting it was correct: two of the
four earn nothing against the brief.

**Everything remaining goes on what the brief asks for**: the AI analysis
service, the dashboard against real data, the analysis history, the README, the
demo script and the methodology writeup. All six are named deliverables.

| Extra | Verdict |
|---|---|
| Crypto wallet BFS | **Kept** — already built and merged, costs nothing more |
| MkDocs Material | **Kept** — already scaffolded, renders docs that exist anyway |
| Helm chart + kind | **Cut** — nowhere in the brief |
| Grafana + golden signals | **Cut** — nowhere in the brief |
| ArgoCD | **Cut** — was already only declared |

The cut work is not hidden. The README says plainly that deployment manifests
were out of scope for the time budget and describes how the application would be
deployed, which costs a paragraph and loses nothing: the author maintains exactly
that stack at CERN and can speak to it in the interview.

If budget later allows, the order to restore is Helm, then Grafana, then live
ArgoCD — and only after every spec item is complete and the demo has been
rehearsed out loud.

## Stack — pinned, do not drift

| | |
|---|---|
| Java | **25** (LTS) via Gradle toolchain |
| Spring Boot | **4.1.1** — note Boot 4 starter names: `-webmvc`, per-module `-test` |
| Build | Gradle Kotlin DSL, wrapper only (`./gradlew`) |
| Database | PostgreSQL 17 + pgvector |
| Frontend | React + TypeScript + Vite + Tailwind. **No component library.** |
| LLM | Claude Opus 5 (`claude-opus-5`) behind a port, with a stub adapter |

## Architecture

```
core/     PURE JAVA. No Spring, no JPA, no SQL, no HTTP. Imports nothing framework-shaped.
domain/   JPA entities, repositories, window-function queries.
ai/       LlmClient + Retriever ports. Claude and stub adapters.
security/ Operators, argon2id, session.
service/  Use cases.
api/      REST controllers, DTOs, error mapping.
```

Dependencies point strictly downward. `ArchitectureTest` fails the build if that
is ever violated — do not weaken it to make code compile, fix the code.

## Rules that are easy to get wrong

- **The AI never decides risk.** Rules produce the score; the model explains it
  and proposes actions. `ai_analyses` stores the computed level *and* the
  model's own assessed level, separately, on purpose.
- **One rule, one class, one test.** A rule is a tiny self-contained component.
  Adding a rule must never require editing the scorer.
- **Weights are data.** They live in `risk_rules.weight` and change with an
  `UPDATE`. `threshold_logic` is the human-readable statement of the condition,
  bound to its Java class by `rule_code`.
- **Every rule that fires writes a `risk_assessments` row.** That is the audit
  trail; it is not optional.
- **Passwords are hashed with argon2id**, never encrypted. Encryption is
  reversible and this is a bank.

## Code style

- Records for DTOs and value objects. Entities are plain classes (JPA needs a
  no-arg constructor and mutability).
- `final` by default. Immutable unless there is a reason.
- Stream pipelines over accumulating loops. Indexed `for` only in the scoring
  hot path and the BFS inner loop.
- Lambdas are local subroutines — scoped to their routine, a couple of
  expressions at most. Anything wanted elsewhere becomes a named method.
- **No Lombok.** Records cover the boilerplate; annotation processing stays out
  of the build.
- `Optional` at boundaries, never as a field.
- Spotless + palantir-java-format. `./gradlew spotlessApply` before committing.

## Testing

- JUnit 5 + AssertJ. Test names read `given_when_then`.
- `core/` tests boot nothing — no Spring context, no database. If a core test
  needs either, the design is wrong.
- Repository and window-function tests use Testcontainers against real
  PostgreSQL. Never H2 — we rely on pgvector, BRIN and generated columns.
- `ArchitectureTest` is part of the suite, not an optional extra.

## Working as a subagent here

- You own one task and one worktree. Stay inside your file scope.
- Commit to your own branch. **Never merge** — the supervisor verifies and merges.
- Run `./gradlew spotlessApply check` before you report. Report honestly: if
  something does not pass, say so rather than describing intent.
- Author commits as `sneakyjbras <j.eduardo.bras@outlook.com>` (already set in
  the repo config — a plain `git commit` does the right thing).
