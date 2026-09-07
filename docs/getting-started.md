# Getting Started

## Prerequisites

- **Docker**, with the compose plugin (`docker compose version` should work).
- **Node** for the frontend (`npm run dev`).
- Nothing else. Java 25 is fetched by the Gradle toolchain the first time the
  backend builds — there is no local JDK to install, and no local PostgreSQL
  to configure.

## Starting the backend

```bash
./start.sh
```

This does three things, in order:

1. Brings up the `pgvector/pgvector:pg17` container from `docker-compose.yml`
   and waits for `pg_isready` before continuing — up to 60 seconds, then it
   gives up loudly rather than letting the backend fail against a database
   that isn't listening yet.
2. Reports which LLM mode is about to run: Claude if `ANTHROPIC_API_KEY` is
   set in the environment, the deterministic stub if it is not.
3. Runs `./gradlew bootRun`. Flyway applies the four migrations on boot
   (`V1__baseline`, `V2__seed_operators`, `V3__seed_policy_corpus`,
   `V4__seed_demo_data`), and the policy corpus is embedded at startup — see
   `PolicyCorpusEmbeddingInitializer` — so the first boot takes longer than
   later ones. The API is on <http://localhost:8080> once it's up.

Other invocations:

```bash
./start.sh --down    # stop the containers, keep the database volume
./start.sh --reset   # stop the containers, delete the volume too
./start.sh --help    # this, from the script itself
```

### The LLM key is optional, not a placeholder

Without `ANTHROPIC_API_KEY`, `LlmClientConfiguration` wires in
`StubLlmClient`: a deterministic adapter that produces the same
`AnalysisVerdict` shape offline, so the whole analysis feature — level,
summary, recommendations, citations — is demonstrable with no credentials and
no network call. The interface names which provider actually answered, on
every analysis. Export the key and `AnthropicLlmClient` takes over instead,
calling `claude-opus-5` with adaptive thinking and structured output — no code
path changes, just which bean `LlmClient` resolves to.

## Starting the frontend

```bash
cd frontend
npm install
npm run dev
```

Vite serves the SPA, by default on <http://localhost:5173>, and proxies every
`/api/**` request to the backend on `:8080` (see `vite.config.ts`). That
proxy isn't incidental: the session cookie `SecurityConfig` issues is
`SameSite=Strict`, which only survives if the browser sees one origin for
both the SPA and the API. Run the frontend on its own port talking cross-origin
to the backend and the cookie will not come back on subsequent requests.

## Logging in

Two operators are seeded by `V2__seed_operators.sql`, one of each role:

| Username   | Password                | Role         |
|------------|--------------------------|--------------|
| `e.rossi`  | `Operator-Demo-2026`     | `OPERATOR`   |
| `m.keller` | `Supervisor-Demo-2026`   | `SUPERVISOR` |

These are throwaway credentials seeded straight into a disposable database —
never real accounts, never reused anywhere else. The migration stores
argon2id hashes produced by
`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` — the same encoder
`OperatorAuthenticationProvider` verifies against at login — and each hash was
confirmed to round-trip through `PasswordEncoder.matches(...)` before being
committed, not guessed and not copied from an unrelated tool.

Login is session-based and needs a CSRF token first. The SPA's login form
handles this for you; from the command line the same three calls look like:

```bash
curl -c cookies.txt http://localhost:8080/api/auth/csrf
curl -b cookies.txt -c cookies.txt \
  -H "X-XSRF-TOKEN: $(grep XSRF-TOKEN cookies.txt | cut -f7)" \
  -H "Content-Type: application/json" \
  -d '{"username":"e.rossi","password":"Operator-Demo-2026"}' \
  http://localhost:8080/api/auth/login
curl -b cookies.txt http://localhost:8080/api/auth/me
```

The first call sets the `XSRF-TOKEN` cookie (`CsrfCookieFilter`); the second
sends it back as a header, which is what a same-origin double-submit cookie
scheme checks; the third confirms the session by reading back the logged-in
operator.

## Where things are in the app

| Route | What's there |
|---|---|
| `/login` | The login form. |
| `/customers` | Search by reference or UUID — there is no name search. |
| `/customers/:idOrReference` | A customer's activity, current risk score, the "Run analysis" action, and analysis history. |
| `/styleguide` | The frontend's own design-system reference page — not part of the product, useful when checking a UI change against the rest of the app. |

## The seeded customers

`V4__seed_demo_data.sql` seeds seven customers, each built to demonstrate one
specific thing about the rule engine. Log in as `e.rossi` and search these
references (search accepts a `reference` like `CH-7002-4488` or a raw UUID —
typing a UUID by hand isn't something an operator does, but the search
endpoint accepts either):

| Reference | Customer | Level | What it demonstrates |
|---|---|---|---|
| `CH-7002-4488` | Sandra Wyss | **CRITICAL** | Five payments between 9,300 and 9,750 to the same Myanmar account across six days. `R-02` (elevated-risk corridor) fires on the first payment; `R-01` (structuring) joins at the third. No single row looks wrong — the pattern exists only in the seven-day window. |
| `CH-7002-4471` | Livia Baumann | **LOW** | The control: ordinary retail activity, nothing fires. Without a customer who scores zero, there is no evidence the scorer discriminates rather than flagging everyone. |
| `CH-7002-4525` | Reto Zimmermann | **LOW** | Card declines and a cross-border payment that look irregular on their face and correctly trip nothing — the deliberate absence of a false positive. |
| `CH-7002-4518` | Thomas Egger | **HIGH** | `R-03` — card-not-present declines across several merchants inside one hour. Card testing. |
| `CH-7002-4482` | Julian Meier | **HIGH** | `R-04` (rapid exchange disposal) and `R-05` (flagged wallet proximity, at two hops via the BFS over the wallet graph) firing together. |
| `CH-7002-4501` | Priya Nair | **MEDIUM** | `R-06` (dormancy burst) and `R-07` (quasi-cash concentration) — two behaviourally weak signals that only add up to something worth a look when they land on the same day. |

The seventh seeded customer, Kenji Watanabe (`CH-7002-4495`), isn't in the
README's table because he doesn't carry a finding of his own — he's the
intermediary wallet in Julian Meier's chain. The seeded transfer graph runs
Julian Meier → Kenji Watanabe → the configured flagged address
(`gotthard.risk.flagged-wallets`), which is exactly the two-hop path `R-05`'s
breadth-first search is walking when it fires on Julian Meier at half weight.

For each of the six above, open the customer, look at the activity feed, note
the score and which rules are named against it, then click **Run analysis**.
The result names a risk level, summarises what was found, recommends what the
operator should do, and cites the exact policy sections it was shown — grounded
in the codes that fired, never in free text. Run it more than once and the
history panel keeps every prior analysis, attributed to the operator who ran
it, with the model, prompt version and token/latency provenance that produced
it.
