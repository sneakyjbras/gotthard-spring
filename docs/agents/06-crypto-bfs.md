# Agent 06 — Crypto wallet graph traversal

**Model:** Sonnet · **Wave:** 2 · **Scope:** `core/graph/`

Sonnet because BFS is a known algorithm and the modelling was settled in the
architecture. R-05 in the merged risk core already expects a hop distance.

## Instructions given
> Depth-limited BFS over the directed wallet graph from `crypto_activity`, returning
> hops to the nearest flagged address. Pure Java, no Spring, no SQL — the edge set is
> passed in. Must terminate on cycles and respect the depth cap.
