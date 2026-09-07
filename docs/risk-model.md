# Risk Model

## Rules decide, the model never does

A `RiskScore` between 0 and 100 is produced entirely by summing the
`contribution()` of whichever of the seven rules below `fires()` against a
transaction's `Features`. Nothing about that number depends on a model call —
the AI analysis is requested afterwards, against a score that is already
final. `AnalysisVerdict` carries the model's own `assessedLevel`, but it is
stored in a separate `ai_analyses` column from `computed_level`, with
`levels_diverged` as a generated column over the two. A disagreement is a
review signal to surface, not something either side is allowed to resolve on
the other's behalf.

## The seven rules

`V4__seed_demo_data.sql` seeds one `risk_rules` row per rule, `rule_code`
binding it to its Java class. `threshold_logic` is that class's condition
restated as a sentence for an operator to read; `weight` is what firing is
worth on the 0–100 scale.

| Code | Rule | Class | Applies to | Weight | Condition |
|---|---|---|---|---|---|
| `R-01` | Near-Threshold Structuring | `NearThresholdStructuringRule` | `ALL` | 44.00 | Three or more transactions in the trailing 7 days each sitting just under the 10,000 reporting threshold, together totalling 10,000 or more. Channel-agnostic — the same behaviour shows up in cards, transfers and crypto alike. |
| `R-02` | Elevated-Risk Payment Corridor | `ElevatedRiskCorridorRule` | `PAYMENT` | 32.00 | A payment to a beneficiary bank in an elevated-risk jurisdiction (`AF, HT, IR, KP, MM, SY, YE`), where the customer has sent two or more such cross-border payments in the trailing 7 days, or their combined value in that window reaches 5,000 or more. |
| `R-03` | Card-Not-Present Decline Cluster | `CardNotPresentDeclineClusterRule` | `CARD` | 52.00 | Three or more card-not-present declines within a rolling 1-hour window, spread across two or more distinct merchants — the pattern a stolen card number leaves while it's being validated. |
| `R-04` | Rapid Exchange Disposal | `RapidExchangeDisposalRule` | `CRYPTO` | 34.00 | Crypto that arrived in a wallet is sent on to a known exchange within 1 hour, with 10,000 or more having left that wallet over the trailing 24 hours. |
| `R-05` | Flagged Wallet Proximity | `FlaggedWalletProximityRule` | `CRYPTO` | 54.00 | The sending wallet is within 2 hops of an address on the flagged-wallet watch list, found by a depth-limited walk of the wallet graph. Direct contact (0–1 hops) prices at the full weight; one intermediary wallet away (2 hops) prices at half. |
| `R-06` | Dormancy Burst | `DormancyBurstRule` | `ALL` | 20.00 | An account silent for 90 days or more suddenly transacts 5 or more times within a single day — the shape a taken-over or rented account leaves behind. |
| `R-07` | Quasi-Cash Concentration | `QuasiCashConcentrationRule` | `CARD` | 18.00 | 2,500 or more of a single day's card spend concentrated at quasi-cash, money-transfer or gambling merchants — the card-side equivalent of a cash machine. |

`applies_to` mirrors `Rule.appliesTo()`: `RiskScorer` only asks a rule to
evaluate a transaction on the channel it declares for. `ALL` — `R-01` and
`R-06` — means the pattern is channel-agnostic by design; the other five each
speak for exactly one of `CARD`, `PAYMENT` or `CRYPTO`.

## Weights are tuned against the bands, on purpose

The seven weights above aren't arbitrary — the seed migration's own comment
explains how they were chosen against `RiskLevel`'s bands (LOW at 0, MEDIUM at
25, HIGH at 50, CRITICAL at 75):

- A single "primary" typology rule — structuring, an elevated corridor, card
  testing, rapid crypto disposal, direct flagged-wallet contact — lands on its
  own in the MEDIUM-to-HIGH range: worth an operator's attention, not yet a
  foregone conclusion.
- Two primary signals compounding on the *same* transaction — exactly what
  happens when a structuring payment also runs a high-risk corridor — clears
  the CRITICAL floor. The compounding is the point: neither rule alone is
  dispositive, together they are.
- The two "behavioural" rules — dormancy-then-burst, quasi-cash concentration
  — are the lightest on purpose. Each is real but weaker alone (a returning
  traveller; a legitimate flutter at the casino), so a single hit stays
  LOW/MEDIUM, and it takes both firing on the same transaction to clear into
  MEDIUM.

The seeded demo customers make this concrete — these are the exact totals
`RiskScorer.score(...)` produces for the transactions that carry them:

| Customer | Rules fired | Total | Band |
|---|---|---|---|
| Sandra Wyss, first two payments | `R-02` alone | 32.00 | MEDIUM |
| Sandra Wyss, third payment onward | `R-01` + `R-02` together | 76.00 | CRITICAL |
| Thomas Egger | `R-03` alone | 52.00 | HIGH |
| Julian Meier | `R-04` + `R-05` at 2 hops | 61.00 | HIGH |
| Priya Nair | `R-06` + `R-07` together | 38.00 | MEDIUM |

## How banding works

`RiskLevel` is an enum of four bands, each carrying only the score at which it
opens:

```java
LOW("0.00"), MEDIUM("25.00"), HIGH("50.00"), CRITICAL("75.00")
```

`RiskLevel.forScore(score)` returns the highest band whose
`inclusiveLowerBound()` is at or below the score — a `stream().filter(...).
reduce((lower, higher) -> higher)` over the four constants, not a chain of
`if`/`else` that has to stay in sync with the table above by hand. Add a band
in the future and this method doesn't change; it just has one more constant to
consider.

`RiskScore` itself is the value this is evaluated against: a `BigDecimal`
clamped to `[0.00, 100.00]` (`RiskScore.of(...)` clamps; the canonical
constructor rejects anything outside the range as a programming error). Enough
rules firing at once can sum past 100 — clamping to 100 is what "as bad as it
gets" is defined to mean, rather than a score that keeps climbing without
bound.

## Why weights are data and logic is code

Every rule is a class implementing four things and nothing more:
`code()`, `appliesTo()`, `fires(Features)`, and an optional
`contribution(Features, weight)` that defaults to returning the weight
unchanged. `RiskScorer` imports no concrete rule — it holds a `List<Rule>`,
filters by channel, filters by `fires()`, prices what's left, and sums. That
split is deliberate on both sides of the line:

- **Logic is code because it needs a test, and a test needs a compiler.**
  `NearThresholdStructuringRule.fires(...)` is three lines reading
  `Velocity.nearThresholdCount7d()` and `rollingVolume7d()`. Getting that
  condition right — and keeping it right when someone touches it — is exactly
  what `given_when_then` unit tests are for. A condition expressed as a SQL
  string in `threshold_logic` would not be unit-testable, and would be an
  injection surface for no benefit.
- **Weights are data because they change on a policy cycle, not a release
  cycle.** `risk_rules.weight` is read fresh on every evaluation through the
  `RuleWeights` port — `RiskEvaluationService` loads the current
  `risk_rules` table and passes it into `RiskScorer.score(features, weights)`
  each time, rather than a scorer caching weights at construction and going
  stale. Retuning `R-05` from 54.00 to 60.00 is an `UPDATE` statement; it
  reaches every subsequent evaluation with no deploy.
- **A rule with no enabled weight is switched off, not broken.**
  `RuleWeights.weightFor(ruleCode)` returns `Optional.empty()` for a code with
  no row, or a row with `enabled = FALSE`, and `RiskScorer` simply excludes
  that hit from the total. Disabling a rule is the same `UPDATE` as retuning
  one.
- **One exception, and it lives in Java on purpose.** `R-05` is the one rule
  that grades its own severity: `FlaggedWalletProximityRule.contribution(...)`
  multiplies the weight by `1.0` for 0–1 hops or `0.5` for exactly 2 hops,
  rather than taking the weight as given. That scaling is a property of
  *this* rule's condition — "how close" is graded, not just "whether" — and
  the seed migration's own comment is explicit that this halving lives in the
  rule's `contribution()` override, not in the `risk_rules` table, precisely
  because it isn't a tunable weight; it's part of what the rule means.

Adding an eighth rule is a new class implementing `Rule`, one new line in
`StandardRules.all()`, and one new `risk_rules` row. `RiskScorer.java` never
appears in that diff — see [Architecture](architecture.md#why-the-core-is-pure-java)
for why that property is enforced by tests, not just convention.

## The wallet graph search behind R-05

`R-05` is the only rule whose signal isn't produced by a `FeatureWindowQuery`
window frame — proximity in a graph isn't an aggregate over a timeline, it's a
walk, and it's answered by `core/graph`:

1. **`WalletGraph.build(edges)`** takes every `(from, to)` pair from
   `crypto_activity` (loaded once per evaluation by `WalletGraphQuery`, a
   plain `SELECT DISTINCT`) and groups them by sender, so `neighborsOf(wallet)`
   is a map lookup rather than a scan.
2. **`FlaggedWalletSearch.hopsToNearestFlaggedWallet(...)`** runs a
   breadth-first search, one frontier at a time, from the customer's sending
   wallet(s):
   - The watch list is checked *before* any hop is taken — a wallet already
     on the list costs nothing to detect and is always `0` hops away.
   - Each subsequent frontier expands outward by exactly one hop, and the
     first frontier that intersects the watch list is, by construction, the
     nearest one — not merely the first path a depth-first walk happened to
     stumble onto.
   - The walk never runs past `maxDepth` (`FlaggedWalletProximityRule
     .MAX_HOPS = 2`). An address one hop beyond the cap and an address that's
     genuinely unreachable both collapse to the same
     `CryptoSignals.NO_PATH_TO_FLAGGED_WALLET` sentinel (`Integer.MAX_VALUE`)
     — the rule reading it doesn't need to know, and doesn't ask, which case
     happened.
3. **`FlaggedWalletProximityRule`** fires when
   `hopsToFlaggedWallet <= MAX_HOPS`, and is the one rule that overrides
   `contribution()`: direct contact (0–1 hops) prices at the full weight
   (54.00), one intermediary wallet away (2 hops) prices at half (27.00).
   Anything further out — or unreachable within the search depth — doesn't
   fire at all.
4. **`WalletProximityLoader`** (`service`) is the only thing that knows the
   edge set lives in a table: it loads the current watch list from
   `FlaggedWallets` (backed by `gotthard.risk.flagged-wallets`, empty by
   default) and, only if it's non-empty, builds the graph once per evaluation
   — not once per crypto transfer — and hands `FeatureAssembler` a
   `WalletProximity` port that answers `hopsFrom(wallet)`.

The seeded demo runs this end to end: `gotthard.risk.flagged-wallets` names
one address by default, and the transfer chain Julian Meier → Kenji Watanabe →
that address is exactly two hops. `R-04` (rapid disposal, 34.00) and `R-05` at
two hops (54.00 × 0.5 = 27.00) fire together on Julian Meier's exchange
disposal, totalling the 61.00 HIGH in the table above.
