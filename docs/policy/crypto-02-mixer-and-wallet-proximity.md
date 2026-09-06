# CRYPTO-02 — Wallet Clustering, Mixer Exposure and Flagged-Address Proximity

> **Exercise disclaimer.** This document was written for the gotthard-spring take-home
> exercise. It is not the policy of any real financial institution and must never be
> treated as compliance guidance outside this project.

**Status:** Internal draft · **Effective:** 2026-01-01 · **Owner:** Financial Crime Compliance (exercise)

## 1. Purpose and scope

This document covers exposure to wallets on the firm's watch list through the
blockchain's own transaction graph, and what the console's automated detection
(rule `R-05`) means when it fires. It applies to the crypto channel. Rapid movement of
funds into a plain exchange is a related but distinct pattern, covered separately in
`CRYPTO-01`.

## 2. Definitions

A **flagged wallet** is an address the firm's watch list associates with sanctioned
actors, ransomware, theft, darknet markets or a **mixer** (also called a tumbler) — a
service designed specifically to break the link between a deposit and a withdrawal by
pooling many users' funds together. A **hop** is one transaction distance in the
blockchain's transaction graph: a wallet that pays a flagged address directly is at hop
one; a wallet that pays a wallet that pays a flagged address is at hop two, and so on.
**Wallet clustering** is the practice of grouping addresses that transaction patterns
suggest are controlled by the same actor, which is how proximity is measured
meaningfully rather than address-by-address.

## 3. Detection criteria

`R-05` fires when a depth-limited walk of the wallet transaction graph finds a flagged
address within two hops of the wallet under review. Distance carries the whole signal:
paying a flagged address directly, or receiving directly from one, scores at full
weight; a single intermediary wallet between the two — one hop further out — scores at
half weight. Anything beyond two hops, or unreachable within the search depth, does not
fire the rule at all, since at that distance the graph connects almost every wallet to
almost every other one and the signal stops meaning anything.

## 4. Typical patterns

- A direct transfer to or from an address the watch list already carries.
- A single pass through one intermediary wallet on the way to or from a flagged
  address — the classic use of a "peel chain" to add distance.
- Funds routed through a service the watch list identifies as a mixer, after which the
  destination address itself may look entirely unremarkable.

## 5. Operator actions

Review how many hops separate the customer's wallet from the flagged address before
deciding how urgently to act — a direct transfer warrants faster handling than a single
intermediary hop. Request the customer's explanation for the counterparty relationship.
Do not assume guilt from proximity alone: a wallet can receive an unsolicited transfer
from anywhere, and an exchange's own hot wallet can sit near a flagged address in the
graph purely through the exchange's own volume.

## 6. Escalation and reporting

Escalate a direct (one-hop) match to a supervisor immediately; escalate a one-intermediary
(two-hop) match the same business day. File an Internal Escalation Report per `SOP-01` §6
for any confirmed direct exposure to a flagged address, and for a two-hop match once the
customer's explanation has been reviewed and found insufficient.
