# FRAUD-01 — Card-Not-Present Fraud and Card Testing Patterns

> **Exercise disclaimer.** This document was written for the gotthard-spring take-home
> exercise. It is not the policy of any real financial institution and must never be
> treated as compliance or fraud-operations guidance outside this project.

**Status:** Internal draft · **Effective:** 2026-01-01 · **Owner:** Fraud Operations (exercise)

## 1. Purpose and scope

This document covers card-not-present (CNP) decline clusters and what the console's
automated detection (rule `R-03`) means when it fires. It applies to the card channel
only. Unlike the AML documents in this corpus, the customer here is frequently the
victim, not the suspect — the card number is usually stolen, and the account holder may
not yet know it.

## 2. Definitions

**Card-not-present** means the physical card was never presented to a merchant terminal
— an online purchase, a phone order, a stored-credential charge. **Card testing** is the
practice of running a small number of low-value, high-frequency authorisation attempts
across many merchants to find out whether a stolen card number, expiry date and security
code combination still works, before using it for a larger purchase elsewhere.

## 3. Detection criteria

`R-03` fires when a card accumulates three or more declined card-not-present
authorisation attempts within a rolling one-hour window, spread across two or more
distinct merchants. Both the decline count and the merchant spread are required: a
customer mistyping their own card details repeatedly at one merchant is a support
question, not a fraud pattern; the signature of testing is the same card failing
different merchants in quick succession.

## 4. Typical patterns

- A run of small-value declined attempts (often under CHF 5) at unrelated online
  merchants within a few minutes of each other.
- Authorisation attempts against merchant categories with weak or no address
  verification, tried one after another.
- A quiet card that suddenly generates a burst of CNP attempts with no purchase pattern
  resembling the customer's history.

## 5. Operator actions

Do not wait for a successful fraudulent charge to act — the value of catching a testing
pattern is acting before the tested number is used for real. Contact the customer
through a verified channel (not a channel supplied in the transaction itself) to confirm
whether they recognise the activity. Where the customer does not recognise it, a
temporary hold on the card pending reissue is within an operator's own authority per
`SOP-01` §3; a full account freeze is not, and needs supervisor sign-off per `SOP-01` §4.

## 6. Escalation and reporting

Escalate to a supervisor the same business day whenever the customer does not recognise
the activity, regardless of whether any attempt actually succeeded. File an Internal
Escalation Report per `SOP-01` §6 once a card has been confirmed compromised, so the
pattern is on record even though the customer is the victim rather than the subject of
suspicion.
