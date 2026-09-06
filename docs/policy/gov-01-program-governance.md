# GOV-01 — AML Program Governance, Risk Rating and Escalation Framework

> **Exercise disclaimer.** This document was written for the gotthard-spring take-home
> exercise. It is not the policy of any real financial institution and must never be
> treated as compliance guidance outside this project.

**Status:** Internal draft · **Effective:** 2026-01-01 · **Owner:** Financial Crime Compliance (exercise)

## 1. Purpose and scope

This document explains how the console's risk rating works end to end, and how its
pieces are meant to relate to each other. Where the other documents in this corpus
explain one detection pattern each, this one explains the frame all of them sit inside:
the rating scale, the roles that act on it, and what to do when two independent
assessments of the same activity disagree.

## 2. Risk rating framework

Every assessed transaction and customer window carries a numeric score from nought to a
hundred, which resolves to one of four bands. A score opens a band at the point shown
and holds it up to the next band's threshold:

| Level | Opens at | What it means |
|---|---|---|
| LOW | 0 | Nothing notable fired. Ordinary activity. |
| MEDIUM | 25 | At least one lower-weighted pattern fired, or several together. |
| HIGH | 50 | A serious pattern fired, or several moderate ones compounded. |
| CRITICAL | 75 | Multiple serious patterns, or one pattern at its most severe. |

The exact weight each pattern contributes is tunable data, not fixed in this document —
see `GOV-01` §3 for why, and consult the console's own rule configuration for current
values.

## 3. How detection and the assistant's analysis relate

The console's automated rules compute the score and the level from §2 directly from
transaction data — deterministically, the same input always produces the same output,
and every rule that fires is written to the audit trail. **The rules decide the level;
nothing else does.** An AI assistant separately reviews the same window and produces its
own written summary, proposed actions, and its own independent assessed level. The
assistant's assessed level is recorded alongside the computed level, not instead of it,
and the two are never silently reconciled into one number.

**When the computed level and the assessed level diverge, that divergence is itself a
signal worth an operator's attention** — not a bug to be resolved by picking one over
the other. A lower assessed level than computed may mean the rules caught something
genuinely explainable by context the assistant could see in the narrative; a higher
assessed level than computed may mean the assistant noticed something the rules were
never written to catch. Either way, see `SOP-01` §6: a divergence is grounds for an
Internal Escalation Report on its own.

## 4. Roles and responsibilities

See `SOP-01` §2 for the operator and supervisor roles and what each may do. This
document adds only the program-level point: supervisors are additionally responsible for
keeping rule weights current as patterns evolve, and for the quality of filed Internal
Escalation Reports, not only for individual case decisions.

## 5. Escalation matrix

See `SOP-01` §5 for the operator-facing timing table. At the program level: CRITICAL
findings are reviewed in aggregate weekly regardless of individual case outcomes, to
catch patterns that recur across customers rather than within one customer's history.

## 6. Review, audit trail and this document's own limits

Every rule that fires writes an audit row; every AI analysis records what policy text it
was shown and how well each chunk matched, so a later reviewer can see exactly what
grounded a given write-up. This corpus of nine documents is deliberately short and was
written for a take-home exercise — it is illustrative of how a real policy layer would
be structured and retrieved, not a substitute for one. A real deployment would replace
every document here with the firm's actual, current, legally reviewed policy text, and
would keep it current through a real governance cycle rather than a single commit.
