# Agent 09 — AI analysis service

**Model:** Opus · **Wave:** 3 · **Scope:** `ai/analysis/`, additions to `service/` and
`api/`, plus tests

Opus because prompt design decides demo quality, and because this is the spec item the
whole exercise is named after. Everything below it — the rules, the retrieval, the
schema — already existed; this is the piece that turns them into an answer.

## Instructions given

> Build the AI analysis service for gotthard-spring. This is spec item 2 of the
> assignment and the last missing piece of the brief.
>
> Read `CLAUDE.md` at the repo root FIRST — note the effort split: everything now goes on
> what the brief asks for. Then read:
> - `docs/NEXT-SESSION.md` — states exactly what this task is
> - `backend/src/main/java/ch/gotthard/ai/retrieval/` — the KnowledgeRetriever port already
>   exists and works
> - `backend/src/main/java/ch/gotthard/service/RiskEvaluationService.java` and
>   `CustomerRiskReport` — the risk evaluation you consume
> - `backend/src/main/java/ch/gotthard/domain/model/AiAnalysis.java` and
>   `AiAnalysisCitation.java` — the persistence targets
> - `backend/src/main/resources/db/migration/V1__baseline.sql` — the ai_analyses columns,
>   especially computed_level, assessed_level and the generated levels_diverged
> - `docs/policy/` — the corpus the model will be shown
>
> Scope: `backend/src/main/java/ch/gotthard/ai/analysis/**`, additions to `service/` and
> `api/` for the use case and endpoints, plus tests. Do NOT touch `core/`, `domain/`
> entities, `security/`, `frontend/`, or existing migrations — another agent is working on
> the frontend in parallel.
>
> Deliver:
>
> 1. **An `LlmClient` port** in `ai/analysis/` with two adapters:
>    - **Anthropic adapter** calling Claude. Use the official Java SDK
>      (`com.anthropic:anthropic-java`), model id exactly `claude-opus-5`. Adaptive
>      thinking (`ThinkingConfigAdaptive`); do NOT use budget_tokens, it is rejected on
>      Opus 5. Read the key from `ANTHROPIC_API_KEY`.
>    - **Stub adapter**, deterministic, producing the same structured shape from the
>      signals alone with no network. Selected automatically when no API key is present —
>      use `@ConditionalOnProperty` or an equivalent Spring idiom, and make the app log
>      clearly which one is active.
>
> 2. **Prompt assembly.** The prompt is built from: a compact rendering of the customer's
>    activity in the analysed window, the rules that fired with their human-readable
>    `threshold_logic`, and the policy chunks the retriever returned for those rule codes.
>    Put the stable parts first (system prompt, policy) and the volatile parts last, so
>    prompt caching can work. Version the prompt with a constant that gets persisted to
>    `prompt_version`.
>
> 3. **Structured output.** The model must return risk level (LOW/MEDIUM/HIGH/CRITICAL), a
>    summary, and a list of recommendations. Use structured outputs
>    (`output_config.format`) rather than parsing prose. Handle a refusal `stop_reason`
>    without crashing.
>
> 4. **Persistence.** Write an `ai_analyses` row: the computed score and level from the
>    rules, the model's OWN assessed level in the separate column, summary,
>    recommendations as JSONB, provider, model, prompt_version, token counts, latency, and
>    the raw response. Then `ai_analysis_citations` rows for every policy chunk shown, with
>    its similarity and rank. Attribute to the requesting operator. `levels_diverged` is
>    generated — never write it.
>
> 5. **Endpoints** in `api/`: `POST /api/customers/{id}/analysis` to run one,
>    `GET /api/customers/{id}/analyses` for the history newest-first,
>    `GET /api/analyses/{analysisId}` for one with its citations. Authenticated; the
>    operator comes from the session.
>
> 6. **Tests.** Against the stub adapter, so they need no key and no network: an analysis
>    persists with all fields populated; citations are written and link to real chunks;
>    divergence between computed and assessed level is recorded; history returns
>    newest-first; the endpoints require authentication. Testcontainers
>    `pgvector/pgvector:pg17`, reusing the public `ch.gotthard.TestcontainersConfiguration`.
>    Do NOT write a test that calls the real API.
>
> CONSTRAINTS: `api` must not import `domain` directly — go through `service`. No Lombok.
> Records for DTOs. Stream pipelines over loops. main is at 306 tests, all green; yours
> must keep them green.
>
> Run `./gradlew spotlessApply check` from `backend/`. Commit to your branch. DO NOT MERGE.
>
> Report: the port and adapters, how the prompt is assembled and versioned, the
> structured-output contract, what gets persisted, the endpoints, test count, and the exact
> result of `./gradlew check`. Be honest about anything that does not pass.

## What came back

41 new tests, suite at 347, `./gradlew spotlessApply check` green. Decisions worth
recording, because they were the agent's and not the prompt's:

- The port takes an `AnalysisRequest` in `ai/`'s own vocabulary rather than
  `CustomerRiskReport`, because `ai/` sits below `service/` in the pyramid and taking the
  use case's record would have inverted the dependency. `AnalysisRequestFactory` is the
  whole of that boundary.
- A refusal writes nothing. Storing the computed level as though the model had agreed
  would make `levels_diverged` read `false` for an analysis that never happened — the one
  column whose purpose is to surface disagreement, quietly lying.
- The stub's level comes from the count of distinct rules that fired, not from their
  weighted score. That is a genuinely different reading of the same evidence, so
  divergence arises for the same reason it would with a real model — and the generated
  column becomes testable without a key.
- Both integration suites pin `gotthard.ai.api-key=` empty. Without it, running the suite
  on a machine with `ANTHROPIC_API_KEY` exported would silently bill real API calls.
