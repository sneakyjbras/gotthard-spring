# Agent 09 — AI analysis service

**Model:** Opus · **Wave:** 3 · **Scope:** `ai/analysis/`, `service/`, `api/`

Opus because prompt design decides the quality of the thing being demonstrated, and
because the structured-output contract has to hold for both the real adapter and the stub.

## Instructions given
> Assemble a prompt from the rules that fired plus the policy chunks retrieved for them,
> call Claude behind an `LlmClient` port, parse a structured response into risk level,
> summary and recommendations, and persist to `ai_analyses` with its citations,
> attributed to the operator. A stub adapter must produce the same shape with no API key.
> Store the model's own assessed level alongside the computed one — divergence is signal.
