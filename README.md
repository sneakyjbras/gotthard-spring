# gotthard-spring

Customer activity analytics for financial-services operators. An operator looks
up a customer, sees their card, payment and cryptocurrency activity, and can ask
for an AI-written assessment of the risk that activity carries.

Named for the Gotthard Pass — the way through the mountain.

## Running it

```bash
./start.sh
```

PostgreSQL comes up, migrations apply, the backend starts on
<http://localhost:8080>. Nothing needs configuring.

No API key is required. If `ANTHROPIC_API_KEY` is set the AI analysis calls
Claude; if it is not, a deterministic stub runs instead and the interface says
which one produced the result. `./start.sh --help` covers the rest.

Requires Docker with the compose plugin. Java is fetched by the Gradle
toolchain, so no local JDK 25 is needed.

## Demo login

`V2__seed_operators.sql` seeds two operators, one of each role, so logging in
as different operators is demonstrable without creating accounts by hand:

| Username   | Password                | Role         |
|------------|--------------------------|--------------|
| `e.rossi`  | `Operator-Demo-2026`     | `OPERATOR`   |
| `m.keller` | `Supervisor-Demo-2026`   | `SUPERVISOR` |

**Demo-only.** These are throwaway credentials for this exercise, seeded
straight into a disposable database — never real accounts, and never reused
anywhere else. The seed migration stores argon2id hashes
(`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`), generated and
confirmed to verify with `PasswordEncoder.matches(...)` before being
committed, never guessed.

Session-based login needs a CSRF token first — the SPA does this automatically;
from the command line:

```bash
curl -c cookies.txt http://localhost:8080/api/auth/csrf
curl -b cookies.txt -c cookies.txt \
  -H "X-XSRF-TOKEN: $(grep XSRF-TOKEN cookies.txt | cut -f7)" \
  -H "Content-Type: application/json" \
  -d '{"username":"e.rossi","password":"Operator-Demo-2026"}' \
  http://localhost:8080/api/auth/login
curl -b cookies.txt http://localhost:8080/api/auth/me
```

## Seeing it work

The database seeds seven customers, each of whom exists to demonstrate one thing.
Log in as `e.rossi` and search these references. Search takes a reference or a
UUID — there is no name search.

| Reference | Customer | What it demonstrates |
|---|---|---|
| `CH-7002-4488` | Sandra Wyss | **CRITICAL.** Five payments between 9,300 and 9,750 to the same Myanmar account across six days. The corridor rule fires on the first; structuring joins at the third. No individual row looks wrong — the pattern exists only in the window. |
| `CH-7002-4471` | Livia Baumann | **LOW, nothing fired.** The control. Without a customer who scores zero, there is no evidence the scorer discriminates rather than flagging everyone. |
| `CH-7002-4525` | Reto Zimmermann | **LOW.** Card declines and a cross-border payment that look irregular and correctly trip nothing. Absence of a false positive, deliberately constructed. |
| `CH-7002-4518` | Thomas Egger | **HIGH.** Card-not-present declines across several merchants inside the hour — card testing. |
| `CH-7002-4482` | Julian Meier | **HIGH.** Two hops from a flagged wallet, found by walking the transfer graph, plus rapid disposal to an exchange shortly after inbound funding. |
| `CH-7002-4501` | Priya Nair | **MEDIUM.** Dormant for three months, then a burst in a single day including quasi-cash spend. Two weak signals that only matter together. |

On any of them, run an AI analysis. The result names a risk level, summarises what
was found, recommends what the operator should do, and cites the policy sections
it was shown. Past analyses stay available, attributed to the operator who ran
them.

Without `ANTHROPIC_API_KEY` set, a deterministic stub produces the same structure
offline and the interface says so. The rules-derived score is identical either
way — the model never touches it.


## Architecture

```
core/     pure Java — risk rules, scoring, wallet BFS. No Spring, no JPA, no SQL.
domain/   JPA entities, repositories, window-function queries.
ai/       LlmClient and Retriever ports; Claude and stub adapters.
security/ operators, argon2id, session.
service/  use cases.
api/      REST controllers, DTOs, error mapping.
```

Dependencies point strictly downward, and an ArchUnit test fails the build if
that ever stops being true.

The flow of a single analysis:

```
PostgreSQL   window functions compute features — velocity, near-threshold
             counts, counterparty corridors
     ↓
Java core    one class per rule, weighted, summed into a 0–100 score;
             depth-limited BFS over the wallet graph
     ↓
pgvector     the fired signals retrieve the policy clauses that speak to them
     ↓
LlmClient    Claude, or the stub — signals plus policy become a written
             summary and a set of recommendations
     ↓
persisted    ai_analyses + ai_analysis_citations, attributed to the operator
```

## Design decisions

**The AI does not decide risk.** A deterministic rule engine produces the score;
the model explains it against written policy and proposes actions. This is the
central decision and everything else follows from it. A bank cannot deploy a
system whose risk verdicts cannot be reproduced or audited, and an LLM cannot
offer that. Rules can. The model is given the harder and more useful job:
turning signals into something an operator can act on.

**Both risk levels are stored.** `ai_analyses` keeps the level our rules
computed *and* the level the model itself assessed, in separate columns, with a
generated column flagging when they disagree. Divergence is an audit signal, not
an error to be hidden.

**Rule logic is code; rule weights are data.** Each rule is a small class with
its own test, bound by `rule_code` to a row in `risk_rules` that carries its
weight and a human-readable statement of its condition. Tuning a weight is an
`UPDATE`. Adding a rule is a new class and a new row, and never touches the
scorer.

**Every rule that fires writes a `risk_assessments` row.** That table is the
audit trail the brief asks for, and it is written on every evaluation.

**Passwords are hashed with argon2id, in their own table.** Hashed, not
encrypted — encryption is reversible. Splitting credentials from identity means
operator records can be read without ever loading a hash.

**No Lombok.** Java records cover the boilerplate it used to solve, and the code
on screen is the code that runs.

**PostgreSQL earns its keep**: `pgvector` for policy retrieval, window functions
for velocity and structuring detection, `JSONB` with a GIN index for
variable-shape model output, a `BRIN` index on the append-only transaction
timeline, and a generated column for level divergence.

## Assumptions

The brief refers to a schema attachment that was not included; the six table
definitions printed in the brief itself were taken as authoritative.

`customers` is referenced by the brief but never defined, so its shape is ours.
Operators, persisted analyses, citations and the policy corpus are likewise
ours — the brief requires the behaviour without specifying the tables.

Customers carry a human-readable `reference` (`CH-4410-8821`) alongside their
UUID, and search accepts either. Typing a UUID is not a thing an operator does.

`activity_type`, `status` and the risk levels are `VARCHAR` with `CHECK`
constraints rather than native PostgreSQL enums. Native enums need a custom
Hibernate type and cannot be altered inside a transaction; check constraints map
directly onto `@Enumerated(EnumType.STRING)`.

The policy corpus is written for this exercise. It is plausible and internally
consistent, but it is not any real institution's compliance policy.

## Not included, deliberately

**Deployment manifests.** A Helm chart, a local Kubernetes cluster, GitOps
reconciliation and Grafana dashboards were planned and cut. None of them appears
in the brief, and the time was better spent on what does. The author maintains
exactly that stack — Helm charts reconciled by ArgoCD, kube-prometheus-stack for
metrics — for two production services at CERN, and is happy to talk through how
this would deploy.

**A real embedding model.** Retrieval uses a deterministic local embedder so the
application runs with no credentials and no network. It is a stand-in, labelled
as one in its own javadoc, not a claim to be a semantic model.

**The policy corpus is fiction.** Written for this exercise: plausible,
internally consistent, and not any real institution's compliance policy.


## Documentation

`docs/`, or `mkdocs serve` for the rendered site. `docs/agents/` holds the
instructions given to each AI agent that worked on this repository, recorded as
they were issued.
