# Resuming this project

State at the end of the first build session. Read this first; it should make the
next session productive within a minute.

## Where things stand

`main` is green: **306 tests, 0 failures**, pushed. Waves 0, 1 and 2 are merged.
Every spec item has working backend except the AI analysis itself.

```
✅ wave 0  foundation · 12-table schema · ArchUnit · start.sh
✅ wave 1  JPA domain · argon2id auth · pure-Java risk core · frontend scaffold
✅ wave 2  activity API + SQL windows · wallet BFS · policy retrieval · seed data
⬜ wave 3  AI analysis service · dashboard against real data
⬜ wave 4  analysis history · README · demo script and rehearsal
```

## What is left, in priority order

Everything below is scored by the brief. Nothing else should be built.

1. **AI analysis service** (`ai/`) — assemble a prompt from the rules that fired
   plus the policy chunks retrieved for them; call Claude behind the existing
   port; parse a structured response into risk level, summary and
   recommendations; persist to `ai_analyses` with its citations, attributed to
   the operator. A stub adapter must produce the same shape with no API key.
   Opus: prompt design decides demo quality.

2. **Dashboard against the real API** — replace `mock-client.ts` with real fetch
   calls. Customer detail, activity table per channel, the risk score with the
   rules that fired. The port is already there; this is meant to be a one-file
   swap plus the screens. Sonnet.

3. **Analysis panel and history** — run an analysis, show level, summary,
   recommendations and cited policy; list past analyses with operator and
   timestamp. This is spec item 5's visible half. Sonnet.

4. **README, demo script, rehearsal** — supervisor writes these. The demo is an
   explicitly named deliverable and must be rehearsed out loud, more than once.

## Known gaps to close

- **Nothing defines the flagged-wallet set.** `FlaggedWalletSearch` takes it as a
  parameter and no table or constant supplies one, so rule R-05 can never fire in
  the running application. `ElevatedRiskJurisdictions` is the pattern to copy.
  The seeded chain is documented in `V4__seed_demo_data.sql` under `WALLET_CHAIN`.
- The frontend still authenticates against its mock, not the real session.

## The demo, when it exists

Log in as `e.rossi` / `Operator-Demo-2026`. Search **`CH-7002-4488`** — Sandra
Wyss. Five payments between 9,300 and 9,750 to the same Myanmar account across
six days: the corridor rule fires from the first, structuring joins at the third,
total 76, **CRITICAL**. Then search `CH-7002-4471` — Livia Baumann, clean, LOW,
nothing fires. The contrast is the point.

## Working practices that earned their keep

- Give every agent a **disjoint file scope**, and verify the diff against the
  *merge base* before merging — not against `main`, which includes commits the
  agent never saw. This caught a worktree collision.
- Verify the **merged tree**, never just the branch.
- Never pipe a build through `tail`; you get `tail`'s exit code.
- Prune merged worktrees. Seven stale ones and five Gradle daemons exhausted
  memory and killed background tasks.
