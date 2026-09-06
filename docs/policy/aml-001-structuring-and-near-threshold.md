# AML-001 — Structuring and Near-Threshold Reporting Behaviour

> **Exercise disclaimer.** This document was written for the gotthard-spring take-home
> exercise. It is not the policy of any real financial institution, does not reproduce
> any real institution's or regulator's text, and must never be treated as compliance
> guidance outside this project.

**Status:** Internal draft · **Effective:** 2026-01-01 · **Owner:** Financial Crime Compliance (exercise)

## 1. Purpose and scope

This document tells an operator what structuring looks like in customer activity, and
what the console's automated detection (rule `R-01`) means when it fires. It applies to
card, payment and crypto activity alike — structuring is a behaviour, not a channel, and
a customer who cannot split cash deposits will happily split card loads or transfers
instead.

## 2. Definitions

**Structuring** (also called smurfing) is breaking one transaction that would trigger a
reporting obligation into several smaller ones that individually would not. **Near-threshold**
describes an amount placed just under the reporting line on purpose — not
CHF 4'000, but CHF 9'700, CHF 9'850, CHF 9'900. A single near-threshold payment proves
nothing; a pattern of them does.

## 3. Detection criteria

`R-01` fires when a customer places three or more near-threshold amounts within a
rolling seven-day window, and those amounts aggregate to CHF 10'000 or more across the
same window. Both conditions are required: three small amounts that never add up to
anything is a customer who transacts often, and one large aggregate reached through a
single payment is simply a large payment. It is the count and the total together that
describe someone managing around a line they know exists.

## 4. Typical patterns

- Three transfers of CHF 9'500, CHF 9'600 and CHF 9'800 to the same beneficiary inside
  four days.
- A sequence of card loads that creeps upward in count during a single week, each one
  comfortably under the threshold on its own.
- Round-number amounts that cluster just below CHF 10'000 rather than being spread
  randomly the way ordinary spending is.

## 5. Operator actions

Read the transaction list behind the finding before doing anything else — the pattern
should be visible in the numbers, not just asserted by the score. Request the customer's
own explanation and supporting documentation for the underlying purpose of the funds
(invoice, contract, source of funds). Do not tell the customer that transactions under a
specific amount avoid reporting — that framing is itself a customer-care outcome
attracts unwanted scrutiny, and confirming the exact threshold to a customer defeats the
control. See `SOP-01` for what may be said and done without a supervisor.

## 6. Escalation and reporting

A confirmed structuring pattern is escalated to a supervisor the same business day.
Where the aggregate reaches CHF 10'000 or more with no credible business explanation,
file an Internal Escalation Report per `SOP-01` §6 regardless of the account's prior
history — a clean record is not a defence against a pattern seen in the current window.
