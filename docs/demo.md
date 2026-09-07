# Demo runbook

Twelve minutes of content for a fifteen-minute slot. Rehearse it out loud at
least twice — the timings only hold if the words are already familiar.

## Before you start

- [ ] `./start.sh` running, backend up on :8080
- [ ] `npm run dev` running, browser open on the login page
- [ ] Logged out, so the login itself is part of the demo
- [ ] `ANTHROPIC_API_KEY` exported if you want a live model call. If it is not
      set, the stub runs and the interface says so — say that out loud rather
      than letting them notice it
- [ ] A second browser tab on `/styleguide`, in case design comes up
- [ ] A terminal ready with `psql` for the audit-trail moment
- [ ] Phone on silent, notifications off

## 0:00 — What this is (1 min)

> "A tool for a customer-care operator at a bank. They look up a customer, see
> their card, payment and crypto activity, and can ask for an assessment of the
> risk it carries."

Then state the thesis immediately, because everything else follows from it:

> "The important decision is that the AI does not decide risk. A deterministic
> rule engine produces the score. The model explains what the rules found,
> against written policy, and recommends what to do about it."

## 1:00 — Why that way (2 min)

> "A bank cannot deploy a system whose risk decisions can't be reproduced or
> audited. An LLM can't offer that — ask it twice and you may get two answers.
> Rules can. So the rules decide, and the model gets the harder, more useful job:
> turning signals into something an operator can act on."

Mention the consequence, because it is the part people miss:

> "It also means the analysis is grounded. The rules that fire choose which
> policy sections the model is shown. Every citation traces back to a rule that
> actually triggered — not to something the model found persuasive."

## 3:00 — The clean customer first (1 min)

Log in as `e.rossi`. Search **`CH-7002-4471`** — Livia Baumann.

> "LOW. Nothing fired."

> "I'm showing you this one first on purpose. Anyone can build a system that
> flags everybody. The interesting question is whether it stays quiet."

## 4:00 — The one that matters (3 min)

Search **`CH-7002-4488`** — Sandra Wyss. Scroll the payment activity.

> "Five payments over six days. Nine four fifty. Nine six eighty. Nine three
> hundred. Nine seven fifty. Nine five forty. All to the same account in
> Myanmar."

Pause there. Let them see it.

> "No single one of those is unusual. Below any reporting threshold, ordinary
> amounts, ordinary customer. The pattern only exists across the window — which
> is why this is computed with a rolling seven-day window function in PostgreSQL
> rather than by looking at rows."

Show the score: **CRITICAL, 76**, with the two rules named and their conditions
in plain English.

> "The corridor rule fires on the first payment. Structuring joins at the third.
> The operator can see exactly which rules moved the number and why."

## 7:00 — The analysis (3 min)

Run it. While it thinks:

> "This is the only place the model is involved."

When it returns, walk the four parts: level, summary, recommendations, citations.

> "It cites the policy sections it was shown. Those weren't retrieved from
> keywords — they were retrieved because those specific rules fired."

If computed and assessed levels differ, stop and point at it:

> "The rules said one thing, the model said another. We store both and flag the
> disagreement rather than hiding it. That's a review signal, not a bug."

Then the history:

> "Analyses are persisted with the operator who ran them, the model and prompt
> version used, and the policy they cited. Six months later, someone can see
> exactly what evidence produced this verdict."

## 10:00 — The audit trail (1 min)

Switch to the terminal:

```sql
SELECT r.rule_code, count(*) FROM risk_assessments a
JOIN risk_rules r USING (rule_id) GROUP BY 1 ORDER BY 1;
```

> "Every rule that fires writes a row. Rule logic lives in Java, one class per
> rule, each with its own test. The weights live in the database — tuning the
> model is an UPDATE, not a release."

## 11:00 — How it was built (1-2 min)

They asked about methodology, so do not skip this.

> "One Claude session acted as supervisor: it planned, wrote the schema, and did
> every merge. It wrote no feature code. The implementation was done by
> subagents, each in its own git worktree with a disjoint file scope, working in
> parallel waves."

> "Opus where a wrong decision propagates — the rule abstraction, the window
> functions, the prompt. Sonnet where the answer was specified and the work was
> careful execution."

> "Every agent's prompt is committed in docs/agents. The git history shows what
> ran in parallel. What went wrong is written down too — worktree isolation
> doesn't survive a resume, and two agents briefly shared a directory."

## Questions they will ask

**"What if the model is wrong?"** — It cannot change the score. Worst case it
writes a poor summary, and the operator still has the rules, the evidence and
the policy.

**"How do you add a rule?"** — A class and a row. The scorer never changes.
Show `RiskScorer` if they want it — it references no concrete rule.

**"Why not put the logic in the database?"** — Rule logic in `threshold_logic`
strings would not be unit-testable and would be an injection surface. Weights
are data because they change often; logic is code because it needs tests.

**"How would you deploy this?"** — Helm chart reconciled by ArgoCD,
kube-prometheus-stack for metrics. Deliberately out of scope here because the
brief did not ask for it. Two services at CERN run that way.

**"Does it scale?"** — The window functions are indexed; transactions carry a
BRIN index because they are append-only and time-ordered. The LLM call is the
slow part, which is why it is explicit and asynchronous rather than on every
page load.

## If something breaks

Do not debug in front of them. Say what you expected, move to the next thing,
and offer to show it afterwards. A calm recovery reads better than a fix.

The stub adapter is the safety net: if the API call fails, it produces the same
structure offline. Have `ANTHROPIC_API_KEY` unset as a fallback plan.
