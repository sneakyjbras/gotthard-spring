# Policy corpus

The knowledge base behind RAG (spec item 4): nine short compliance documents that ground
the AI assistant's write-ups in policy text the deterministic risk rules actually
selected, rather than in whatever the model would otherwise say unprompted. See
`ai/retrieval/` for the retrieval mechanism and `V3__seed_policy_corpus.sql` for how
these documents reach the database.

**None of this is real.** Every document below was written for the gotthard-spring
take-home exercise, names no real institution, and reproduces no real regulator's or
standard-setter's text. Each file repeats this disclaimer on its own, because each one
is also a unit the AI assistant can cite in isolation.

| Id | Document | Primary rule |
|---|---|---|
| `AML-001` | Structuring and Near-Threshold Reporting Behaviour | `R-01` |
| `SANCTIONS-01` | Elevated-Risk Jurisdictions and Cross-Border Payment Corridors | `R-02` |
| `FRAUD-01` | Card-Not-Present Fraud and Card Testing Patterns | `R-03` |
| `CRYPTO-01` | Crypto Exchange Rapid Disposal and Layering | `R-04` |
| `CRYPTO-02` | Wallet Clustering, Mixer Exposure and Flagged-Address Proximity | `R-05` |
| `AML-002` | Dormant Account Reactivation and Sudden Activity Bursts | `R-06` |
| `AML-003` | Quasi-Cash, Money-Transfer and Gambling MCC Concentration | `R-07` |
| `SOP-01` | Customer-Care Operator Standard Operating Procedure | — (procedure, not a rule) |
| `GOV-01` | AML Program Governance, Risk Rating and Escalation Framework | — (frame, not a rule) |

## Structure every document shares

```
# DOC-ID — Title
> exercise disclaimer
**Status / Effective / Owner**

## 1. Purpose and scope
## 2. Definitions
...
```

`MarkdownPolicyChunker` (`ai/retrieval/`) splits each document at its `##` section
boundaries into one retrievable chunk per section, keeping the document id, the section
number and the section title with it. That heading shape is not a stylistic choice — it
is the chunker's parsing contract, exercised directly by `MarkdownPolicyChunkerTest`.
Front matter before the first numbered section (the disclaimer, the status line) is
intentionally not turned into a chunk: it is not policy substance to retrieve.

`R-01` through `R-07` each map to one primary document above; `SOP-01` and `GOV-01`
are cross-cutting and are not written to be the top hit for any single rule code, but
both documents are cross-referenced by section from every rule-specific document, the
same way a real policy manual links related sections.
