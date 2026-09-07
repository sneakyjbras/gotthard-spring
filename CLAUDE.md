# gotthard-spring

Customer activity analytics for financial-services operators. Take-home for
Swissquote. The global directives in `~/.claude/CLAUDE.md` apply in full —
especially §0 (emergent complexity, the iterative method) and §3 (pipelines,
scoped lambdas). What follows is only what is specific to this repository.

**This project is graded twice: the application, and the methodology used to
build it.** Every agent prompt is written to `docs/agents/NN-task.md` at spawn
time. That file is a deliverable, not a byproduct.

## Effort split — the spec is done; this is the bonus

Every item in the brief is built, tested and documented. What remains is
deliberately *not* in the brief: it exists to be shown at interview, after the
repository has already been submitted.

That ordering is the point. The submitted repository is judged against the brief,
so nothing here may destabilise it. This work lands on `main` after the
submission email, and a failure in it costs nothing.

| Bonus | State |
|---|---|
| Crypto wallet BFS | **Done** — merged in wave 2, R-05 fires against the seeded chain |
| MkDocs Material | Scaffolded; needs content and a served image |
| Dockerfiles + image publishing | To build |
| Helm chart | To build |
| kind cluster | To build |
| ArgoCD, syncing for real | To build |
| Grafana on the golden signals | To build |

Mirror `~/fa/av-tools-infra` — the author maintains it and reviewers may compare:
`chart/` with `values.yaml`, `values-qa.yaml`, `values-prod.yaml`, `_helpers.tpl`
and `NOTES.txt`; `argocd/` with `app-of-apps.yaml`, `applicationset.yaml` and
`appproject.yaml`; `scripts/` for the bring-up.

Nothing in `backend/src` or `frontend/src` changes for this work. If a bonus task
seems to need an application change, stop and say so rather than making it.

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
