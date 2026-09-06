/**
 * Human-readable condition text for each risk rule — this console's own copy
 * of `risk_rules.threshold_logic`, as seeded in
 * `backend/src/main/resources/db/migration/V4__seed_demo_data.sql`.
 *
 * `GET /api/customers/{id}/risk` reports what fired (`RiskFinding`: a code,
 * a name, a score) but not the condition text itself — that column lives on
 * `risk_rules`, which no endpoint exposes today. Duplicating the seven rows
 * here is a deliberate, contained trade-off rather than adding a new
 * backend endpoint out of scope for this change (frontend-only; see
 * `CLAUDE.md`). If a `risk_rules` catalogue endpoint is added later, this
 * file is the one place to delete in favour of it.
 *
 * An unrecognised code — a rule added to the table after this file was
 * written — still renders: `ruleCondition` falls back to a plain notice
 * rather than hiding the finding.
 */
const RULE_CONDITIONS: Readonly<Record<string, string>> = {
  'R-01':
    'Three or more transactions in the trailing 7 days each sitting just under the 10,000 reporting threshold, together totalling 10,000 or more. Channel-agnostic: the same behaviour shows up in cards, transfers and crypto alike.',
  'R-02':
    'A payment to a beneficiary bank in an elevated-risk jurisdiction (AF, HT, IR, KP, MM, SY or YE), where the customer has sent two or more such cross-border payments in the trailing 7 days, or their combined value in that window reaches 5,000 or more.',
  'R-03':
    'Three or more card-not-present declines within a rolling 1-hour window, spread across two or more distinct merchants — the pattern a stolen card number leaves while it is being validated.',
  'R-04':
    'Crypto that arrived in a wallet is sent on to a known exchange within 1 hour, with 10,000 or more having left that wallet over the trailing 24 hours.',
  'R-05':
    'The sending wallet is within 2 hops of an address on the flagged-wallet watch list, found by a depth-limited walk of the wallet graph. Direct contact (0-1 hops) prices at the full weight; one intermediary wallet away (2 hops) prices at half.',
  'R-06':
    'An account silent for 90 days or more suddenly transacts 5 or more times within a single day — the shape a taken-over or rented account leaves behind.',
  'R-07':
    "2,500 or more of a single day's card spend concentrated at quasi-cash, money-transfer or gambling merchants — the card-side equivalent of a cash machine.",
};

/** The condition text for a rule code, or a plain fallback for one this catalogue does not (yet) know. */
export function ruleCondition(ruleCode: string): string {
  return RULE_CONDITIONS[ruleCode] ?? 'Condition text not available for this rule.';
}
