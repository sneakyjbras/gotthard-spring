# CRYPTO-01 — Crypto Exchange Rapid Disposal and Layering

> **Exercise disclaimer.** This document was written for the gotthard-spring take-home
> exercise. It is not the policy of any real financial institution and must never be
> treated as compliance guidance outside this project.

**Status:** Internal draft · **Effective:** 2026-01-01 · **Owner:** Financial Crime Compliance (exercise)

## 1. Purpose and scope

This document covers crypto that arrives in a customer's wallet and is moved straight on
to an exchange, and what the console's automated detection (rule `R-04`) means when it
fires. It applies to the crypto channel. Wallet-graph proximity to a specifically flagged
address is a related but distinct pattern, covered separately in `CRYPTO-02`.

## 2. Definitions

**Rapid disposal** is crypto sent on to an exchange wallet very soon after arriving —
minutes or a small number of hours, rather than the days or weeks an investor typically
holds a position. **Layering** is moving value through a chain of wallets or services
specifically to obscure its origin before it reaches a point where it converts back to
fiat currency; an exchange deposit is usually that point, which is what makes the speed
of arrival-to-deposit meaningful.

## 3. Detection criteria

`R-04` fires when funds arrive in a wallet and are sent on to a recognised exchange
within one hour, and the wallet's total outbound volume over the same rolling
twenty-four-hour window reaches CHF 10'000 or more (converted at the recorded rate).
Neither half is remarkable alone — wallets legitimately receive funds, and wallets
legitimately pay exchanges to cash out — it is the combination of speed and size that
distinguishes a pass-through wallet from an investor's own holdings.

## 4. Typical patterns

- A wallet that has no other activity, receives a transfer, and forwards nearly the
  whole amount to an exchange within minutes.
- A sequence of wallets each holding funds only briefly before passing them one hop
  closer to an exchange — a chain rather than a single hop.
- Deposit timing that clusters immediately after arrival regardless of market
  conditions, which is not how an investor timing a sale behaves.

## 5. Operator actions

Review the wallet's transaction history for the window, not just the single flagged
transfer — a genuine one-off cash-out looks different from a wallet used repeatedly this
way. Request the customer's explanation for the source of the incoming funds and the
reason for immediate disposal. A single rapid disposal with a credible, documented
source (a sale of a personal holding, a payment received in crypto for goods or
services) does not by itself require a freeze.

## 6. Escalation and reporting

Escalate to a supervisor the same business day whenever the source of funds cannot be
explained or documented. File an Internal Escalation Report per `SOP-01` §6 where rapid
disposal repeats across multiple windows, or where the receiving exchange is itself
poorly regulated or previously associated with other escalations — cross-reference
`CRYPTO-02` when the wallet's counterpart is a mixer rather than a plain exchange.
