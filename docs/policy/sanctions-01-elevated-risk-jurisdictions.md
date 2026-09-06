# SANCTIONS-01 — Elevated-Risk Jurisdictions and Cross-Border Payment Corridors

> **Exercise disclaimer.** This document was written for the gotthard-spring take-home
> exercise. It is not the policy of any real financial institution, does not reproduce
> any real regulator's or standard-setter's list, and must never be treated as
> compliance guidance outside this project.

**Status:** Internal draft · **Effective:** 2026-01-01 · **Owner:** Financial Crime Compliance (exercise)

## 1. Purpose and scope

This document covers cross-border payments whose beneficiary bank sits in a
jurisdiction the firm treats as elevated risk, and what the console's automated
detection (rule `R-02`) means when it fires. It applies to the payment channel only —
card and crypto cross-border exposure are covered by `FRAUD-01` and `CRYPTO-01`/`CRYPTO-02`
respectively, since the risk indicators differ by rail.

## 2. Definitions

An **elevated-risk jurisdiction** is a country on the firm's maintained watch list —
standing in for the kind of list a real compliance function tracks against FATF
guidance and similar sources, updated on a policy cycle rather than hard-coded into any
one document. A **corridor** is a repeated or sizeable flow of payments toward the same
elevated-risk destination, as distinct from a single transfer, which is simply a fact
about a customer's life (family abroad, an overseas supplier, a property purchase).

## 3. Detection criteria

`R-02` fires when a payment reaches a beneficiary bank in an elevated-risk jurisdiction
and either of two conditions also holds: two or more such cross-border transfers within
a rolling seven-day window, or a single week's cross-border volume to that jurisdiction
of CHF 5'000 or more. A single small transfer to a listed country, on its own, does not
fire the rule — the corridor is what matters, not the destination alone.

## 4. Typical patterns

- Weekly transfers to the same beneficiary account in an elevated-risk country that
  individually look unremarkable but repeat every cycle.
- A sudden first-time transfer of size to a jurisdiction the customer has no apparent
  connection to in their onboarding profile.
- Multiple small transfers to different beneficiaries that all resolve to banks in the
  same elevated-risk country within a short window.

## 5. Operator actions

Check whether the destination is consistent with the customer's declared profile —
family, business, property, employment — before assuming the worst; a corridor to a
country of birth or prior residence is common and often benign. Where the purpose is not
already on file, request supporting documentation for the transfer (invoice, contract,
relationship evidence). A single elevated-risk transfer with a credible, documented
purpose does not need escalation; a repeating corridor does, whether or not a purpose
has been offered.

## 6. Escalation and reporting

Escalate a confirmed corridor to a supervisor the same business day. Where the customer
cannot or will not explain the purpose of a repeating elevated-risk corridor, file an
Internal Escalation Report per `SOP-01` §6. Do not disclose to the customer that a
specific country triggered review — describe the request as routine documentation only,
per `SOP-01` §6's no-tipping-off rule.
