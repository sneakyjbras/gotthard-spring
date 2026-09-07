# Agent 11 — AI analysis panel and history

**Model:** Sonnet · **Wave:** 3 · **Scope:** `frontend/**`

Sonnet because the design system was settled and approved by then, the backend
endpoints existed and were tested, and the work was building against a known
contract rather than deciding anything.

## Instructions given

> Build the AI analysis panel and history view. This completes spec item 5:
> "AI analysis results should be persisted and available for later review."
>
> Extend the `ApiClient` port and both adapters with the three analysis calls. The
> mock must return believable analyses so the UI is reviewable with no backend.
>
> An analysis panel on the customer detail page. A clear action to run one, and
> while it runs a real pending state — this calls an LLM and can take several
> seconds, so a spinner that conveys "thinking" rather than "loading a table".
> Then show: the risk level the model assessed alongside the level the rules
> computed; **when those two differ, say so plainly** — the backend computes
> `levelsDiverged` for exactly this, and it is an audit signal, not an error, so
> present it neutrally rather than as a warning; the summary; the recommendations;
> and the policy documents cited, with their identifiers.
>
> Analysis history: past analyses newest first, each showing when it ran, which
> operator requested it, and the level. Selecting one shows it in full with its
> citations. This is the "available for later review" half of the spec item and is
> as important as running a new one.
>
> **Provenance must be visible.** Each analysis records the provider and model
> that produced it. Show it. A reviewer running without an API key sees stubbed
> output and must not mistake it for a real model's work.
>
> Handle the 503 the endpoint returns when the model refuses or is unavailable,
> and leave the page usable.
>
> Run the backend and click through if you can; if you cannot, say so plainly
> rather than implying you did.

## What came back

Divergence rendered in plain grey rather than the accent colour, deliberately, so
it reads as something to review rather than something broken. Provenance on every
analysis and every history row, with an explicit line when the stub produced it.

Verified against a running backend, including a genuine divergence — Sandra Wyss
computing CRITICAL while the model assessed HIGH. The agent stated plainly that it
could not open a browser and that the visual result was therefore unverified.
