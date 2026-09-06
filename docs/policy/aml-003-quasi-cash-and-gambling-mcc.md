# AML-003 — Quasi-Cash, Money-Transfer and Gambling MCC Concentration

> **Exercise disclaimer.** This document was written for the gotthard-spring take-home
> exercise. It is not the policy of any real financial institution and must never be
> treated as compliance guidance outside this project.

**Status:** Internal draft · **Effective:** 2026-01-01 · **Owner:** Financial Crime Compliance (exercise)

## 1. Purpose and scope

This document covers card spend concentrated at merchant categories that sit closest to
cash, and what the console's automated detection (rule `R-07`) means when it fires. It
applies to the card channel.

## 2. Definitions

A **merchant category code (MCC)** classifies what kind of business a card
transaction was made at. **Quasi-cash** merchants are ones that sell instruments
readily converted back to cash — money orders, prepaid cards, casino chips, some
cryptocurrency kiosks. **Money-transfer** merchants are wire and remittance services.
**Gambling** merchants sit alongside these because gambling losses and winnings are
themselves a well-known way to give illicit funds a legitimate-looking origin. Together
these three categories are the ones where a card purchase comes closest to simply
withdrawing cash, which is why concentration in them specifically is the signal, not
card spend in general.

## 3. Detection criteria

`R-07` fires when a customer's card spend at quasi-cash, money-transfer and gambling
MCCs together reaches CHF 2'500 or more within a rolling twenty-four-hour window. Which
individual MCCs count toward the total is a data question answered by the query behind
the rule, not by this document; this document only explains what crossing the total
means.

## 4. Typical patterns

- Several purchases at money-order or prepaid-card outlets on the same day, each
  individually unremarkable.
- A concentrated run of gambling-merchant transactions that does not match the
  customer's typical spend pattern.
- Quasi-cash spend that immediately follows a large incoming payment, consistent with
  converting it back toward cash quickly.

## 5. Operator actions

Compare the day's concentration against the customer's ordinary spending pattern before
treating it as remarkable — a customer who regularly uses money-transfer services to
support family abroad is not exhibiting new behaviour just because a threshold sums it
up in one day. Request an explanation where the concentration is new or inconsistent
with the customer's profile. A credible explanation (a one-off large remittance, a
casino visit) closes the matter without further action.

## 6. Escalation and reporting

Escalate to a supervisor the same business day where the concentration repeats across
multiple days or where no credible explanation is offered. File an Internal Escalation
Report per `SOP-01` §6 wherever quasi-cash concentration follows shortly after a
near-threshold or structuring finding under `AML-001` — the combination is a stronger
signal than either alone.
