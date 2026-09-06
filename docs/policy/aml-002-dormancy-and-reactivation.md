# AML-002 — Dormant Account Reactivation and Sudden Activity Bursts

> **Exercise disclaimer.** This document was written for the gotthard-spring take-home
> exercise. It is not the policy of any real financial institution and must never be
> treated as compliance guidance outside this project.

**Status:** Internal draft · **Effective:** 2026-01-01 · **Owner:** Financial Crime Compliance (exercise)

## 1. Purpose and scope

This document covers accounts that go quiet for a long stretch and then transact
heavily in a short window, and what the console's automated detection (rule `R-06`)
means when it fires. It applies to card, payment and crypto activity alike — dormancy
followed by a burst is a behaviour of the account, not of any one channel.

## 2. Definitions

**Dormancy** is an extended stretch with no transaction activity at all — not merely
quiet, but silent. A **burst** is a cluster of several transactions inside a single day
immediately following that silence. Neither half is remarkable alone: dormancy on its
own describes a customer on sabbatical, travelling, or simply not needing the account
for a while, and a busy day on its own describes an ordinary customer having a busy day.
It is the two together, dormancy immediately followed by a burst, that is worth an
operator's attention.

## 3. Detection criteria

`R-06` fires when an account with at least ninety days of continuous silence records
five or more transactions within the following twenty-four hours. The channel does not
matter for either half of the pattern — the ninety days of silence and the five
transactions can each be any mix of card, payment and crypto activity.

## 4. Typical patterns

- An account with no activity for several months that suddenly moves money out across
  several transactions in the same day, consistent with a takeover rather than the
  legitimate owner's return.
- A dormant account that reactivates with a burst of small transactions that look like
  testing whether the account still functions, followed by larger ones.
- Reactivation that coincides with a change to the customer's contact details or
  credentials shortly beforehand — worth checking even though it sits outside this
  rule's own criteria.

## 5. Operator actions

Contact the customer through a verified channel — not one supplied during the burst
itself — to confirm they, and not someone who has taken over the account, are behind
the activity. Request an explanation for both the dormancy and the reactivation; a
credible one (return from travel, a life event, resuming a business) closes the matter
without further action. Where the customer cannot be reached or does not recognise the
activity, a temporary hold on further transactions is within an operator's own authority
per `SOP-01` §3.

## 6. Escalation and reporting

Escalate to a supervisor the same business day whenever the customer cannot be reached
or does not recognise the reactivation. File an Internal Escalation Report per `SOP-01`
§6 wherever account takeover cannot be ruled out, since a taken-over account is both a
fraud loss and a potential money-laundering channel at once.
