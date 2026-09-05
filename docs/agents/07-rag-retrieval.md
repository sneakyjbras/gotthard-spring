# Agent 07 — Policy corpus and retrieval

**Model:** Sonnet · **Wave:** 2 · **Scope:** `ai/retrieval/`, `docs/policy/`, `V3` migration

Sonnet because the retrieval mechanism is plumbing and the corpus is writing to a spec.

## Instructions given
> Eight to ten short compliance policies written for this exercise, chunked and embedded
> into `policy_chunks`. A `KnowledgeRetriever` port with a pgvector adapter and a
> deterministic offline embedder, so retrieval works with no API key. The query is built
> from the rules that fired, not from operator free text.
