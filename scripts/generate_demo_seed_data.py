#!/usr/bin/env python3
"""Generates V4__seed_demo_data.sql -- the narrative demo dataset for gotthard-spring.

Regenerate with:

    python3 scripts/generate_demo_seed_data.py \\
        > backend/src/main/resources/db/migration/V4__seed_demo_data.sql

Determinism
-----------
Every random choice is drawn from one ``random.Random(SEED)`` instance, consumed in
the fixed call order the ``main()`` at the bottom walks through (risk rules, then
each customer in turn). Re-running this script on an unchanged copy of it always
produces byte-identical SQL.

Row identifiers do NOT depend on that RNG sequence at all: every UUID is a
``uuid5(NAMESPACE, "kind:key")`` hash of a stable string, so it stays fixed even if
unrelated code above it in the file changes how many random numbers it draws first.

Timestamps are not baked in as absolute values either. Every row is emitted as
``now() - interval 'N seconds'``, resolved once when the migration actually runs
(PostgreSQL's ``now()`` is the start-of-transaction time, and Flyway applies each
migration in one transaction) -- so "the last 90 days" is fresh relative to
whenever ``V4`` applies, not to whenever this script happened to be run. The
*relative* spacing between rows -- which is what the risk rules key off -- is
identical either way.

What this seeds
----------------
``risk_rules``: all seven codes the core defines (R-01..R-07), each with a weight
and a ``threshold_logic`` sentence rendered from its Java class's javadoc. See the
comment above the risk_rules INSERT for the weighting rationale.

Seven customers, six of them "the demo": a clean retail customer, a noisy-but-clean
one, the near-threshold structuring centrepiece, a crypto customer reaching a
flagged wallet in two genuine hops (plus rapid exchange disposal), a
dormancy-then-burst account, and a card-testing fraud case. The seventh customer
(Kenji Watanabe) is not a headline story -- he exists solely to own the
intermediate wallet the crypto customer's two-hop chain routes through (every
crypto_activity row needs a customer_id; see WALLET_CHAIN below).
"""

from __future__ import annotations

import random
import sys
import uuid
from decimal import ROUND_HALF_UP, Decimal

SEED = 1882  # the year the Gotthard rail tunnel opened
rng = random.Random(SEED)
NAMESPACE = uuid.uuid5(uuid.NAMESPACE_DNS, "gotthard-spring.ch")

TWO_PLACES = Decimal("0.01")
SECONDS_PER_DAY = 86400


# ============================================================================
# Small deterministic helpers -- SQL rendering, ids, money, IBANs, PANs,
# crypto-style addresses. Nothing here is business logic; it is all just
# "produce a realistic-looking, valid-format literal" plumbing.
# ============================================================================


def uid(key: str) -> str:
    """A uuid5 hash of a stable string key -- fixed forever, independent of RNG order."""
    return str(uuid.uuid5(NAMESPACE, key))


def d(x) -> Decimal:
    return Decimal(str(x)).quantize(TWO_PLACES, rounding=ROUND_HALF_UP)


def money(x: Decimal) -> str:
    return f"{x:.2f}"


def q(value) -> str:
    """A quoted SQL string literal, escaping embedded quotes by doubling them."""
    return "'" + str(value).replace("'", "''") + "'"


def qn(value) -> str:
    return "NULL" if value is None else q(value)


def sql_bool(value: bool) -> str:
    return "TRUE" if value else "FALSE"


def ago(days) -> str:
    """``now() - interval 'N seconds'`` for N seconds equal to `days` days (fractional ok)."""
    seconds = round(Decimal(str(days)) * SECONDS_PER_DAY)
    return f"(now() - interval '{seconds} seconds')"


def insert(table: str, columns: list[str], rows: list[list[str]]) -> str:
    cols = ", ".join(columns)
    values = ",\n    ".join("(" + ", ".join(row) + ")" for row in rows)
    return f"INSERT INTO {table} ({cols}) VALUES\n    {values};"


# ---- IBAN (ISO 7064 MOD 97-10 check digits, so these are genuinely valid-format) ----


def _iban_check_digits(country: str, bban: str) -> str:
    rearranged = bban + country + "00"
    numeric = "".join(str(int(ch, 36)) for ch in rearranged)  # 0-9 as-is, A-Z -> 10-35
    return f"{98 - (int(numeric) % 97):02d}"


def _digits(n: int) -> str:
    return "".join(rng.choice("0123456789") for _ in range(n))


def ch_iban() -> str:
    bban = _digits(5) + _digits(12)  # 5-digit bank clearing number + 12-char account
    return f"CH{_iban_check_digits('CH', bban)}{bban}"


def de_iban() -> str:
    bban = _digits(8) + _digits(10)  # 8-digit Bankleitzahl + 10-digit account
    return f"DE{_iban_check_digits('DE', bban)}{bban}"


# ---- card PANs (real Luhn check digit, well-known test BIN prefixes) ----


def _luhn_check_digit(partial: str) -> str:
    total = 0
    for i, ch in enumerate(reversed(partial)):
        n = int(ch) * (2 if i % 2 == 0 else 1)
        total += n - 9 if n > 9 else n
    return str((10 - total % 10) % 10)


def make_pan(bin_prefix: str, length: int = 16) -> str:
    body = bin_prefix + _digits(length - len(bin_prefix) - 1)
    return body + _luhn_check_digit(body)


assert _luhn_check_digit("411111111111111") == "1"  # a well-known Visa test PAN, minus its own check digit

# ---- crypto addresses (format-realistic, not checksum-verified -- synthetic on purpose) ----

BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"


def btc_address() -> str:
    return "1" + "".join(rng.choice(BASE58) for _ in range(33))


def eth_address() -> str:
    return "0x" + "".join(rng.choice("0123456789abcdef") for _ in range(40))


def eth_tx_hash() -> str:
    return "0x" + "".join(rng.choice("0123456789abcdef") for _ in range(64))


def btc_tx_hash() -> str:
    return "".join(rng.choice("0123456789abcdef") for _ in range(64))


def auth_code() -> str:
    return "".join(rng.choice("ABCDEFGHJKLMNPQRSTUVWXYZ23456789") for _ in range(6))


# ============================================================================
# Customer: accumulates one customer's rows and knows how to render its own
# self-contained, commented SQL block (customer -> transactions -> detail
# rows, satisfying FK order within the block).
# ============================================================================


class Customer:
    def __init__(self, key: str, reference: str, full_name: str, country: str, segment: str, onboarded_days_ago):
        self.key = key
        self.customer_id = uid(f"customer:{key}")
        self.reference = reference
        self.full_name = full_name
        self.country = country
        self.segment = segment
        self.onboarded_days_ago = onboarded_days_ago

        self.card_type = rng.choice(["DEBIT", "CREDIT"])
        self.card_pan = make_pan("4" if self.card_type == "DEBIT" else "55")
        self.sender_iban = ch_iban()
        self.domestic_payees = [
            (name, ch_iban())
            for name in rng.sample(
                [
                    "Bruggmann Immobilien AG",
                    "Helvetia Versicherungen",
                    "EWZ Zuerich",
                    "Swisscom AG",
                    "Sanitas Grundversicherung",
                ],
                k=3,
            )
        ]

        self._seq = 0
        self.tx_rows: list[tuple] = []  # (tx_id, activity_type, amount, currency, status, days_ago)
        self.card_rows: list[tuple] = []
        self.payment_rows: list[tuple] = []
        self.crypto_rows: list[tuple] = []

    def _next_tx_id(self, tag: str) -> str:
        self._seq += 1
        return uid(f"tx:{self.key}:{self._seq:04d}:{tag}")

    def card(
        self,
        days_ago,
        amount,
        currency,
        merchant,
        mcc,
        card_present=True,
        status="COMPLETED",
        decline_reason=None,
        pan=None,
    ):
        tx_id = self._next_tx_id("card")
        self.tx_rows.append((tx_id, "CARD", d(amount), currency, status, days_ago))
        code = auth_code() if status == "COMPLETED" else None
        self.card_rows.append(
            (tx_id, pan or self.card_pan, self.card_type, merchant, mcc, card_present, code, decline_reason)
        )
        return tx_id

    def payment(self, days_ago, amount, currency, method, receiver_account, receiver_country, status="COMPLETED"):
        tx_id = self._next_tx_id("pay")
        self.tx_rows.append((tx_id, "PAYMENT", d(amount), currency, status, days_ago))
        self.payment_rows.append((tx_id, method, self.sender_iban, receiver_account, receiver_country))
        return tx_id

    def crypto(
        self, days_ago, amount, currency, blockchain, addr_from, addr_to, tx_hash, exchange_name=None, tag="xfer"
    ):
        tx_id = self._next_tx_id(tag)
        self.tx_rows.append((tx_id, "CRYPTO", d(amount), currency, "COMPLETED", days_ago))
        self.crypto_rows.append((tx_id, blockchain, addr_from, addr_to, tx_hash, exchange_name))
        return tx_id

    def render(self, narrative: str) -> str:
        blocks = [
            f"-- {'-' * 74}\n-- {self.full_name} ({self.reference}) -- {narrative}\n-- {'-' * 74}",
            insert(
                "customers",
                ["customer_id", "reference", "full_name", "country", "segment", "onboarded_at"],
                [
                    [
                        q(self.customer_id),
                        q(self.reference),
                        q(self.full_name),
                        q(self.country),
                        q(self.segment),
                        ago(self.onboarded_days_ago),
                    ]
                ],
            ),
        ]
        if self.tx_rows:
            blocks.append(
                insert(
                    "transactions",
                    ["transaction_id", "customer_id", "activity_type", "amount", "currency", "status", "created_at"],
                    [
                        [q(tid), q(self.customer_id), q(atype), money(amount), q(cur), q(status), ago(days_ago)]
                        for (tid, atype, amount, cur, status, days_ago) in self.tx_rows
                    ],
                )
            )
        if self.card_rows:
            blocks.append(
                insert(
                    "card_activity",
                    [
                        "transaction_id",
                        "card_pan",
                        "card_type",
                        "merchant_name",
                        "mcc_code",
                        "card_present",
                        "authorization_code",
                        "decline_reason",
                    ],
                    [
                        [q(tid), q(pan), q(ctype), q(merch), q(mcc), sql_bool(present), qn(code), qn(reason)]
                        for (tid, pan, ctype, merch, mcc, present, code, reason) in self.card_rows
                    ],
                )
            )
        if self.payment_rows:
            blocks.append(
                insert(
                    "payment_activity",
                    ["transaction_id", "payment_method", "sender_account", "receiver_account", "receiver_bank_country"],
                    [
                        [q(tid), q(method), q(sender), q(receiver), q(country)]
                        for (tid, method, sender, receiver, country) in self.payment_rows
                    ],
                )
            )
        if self.crypto_rows:
            blocks.append(
                insert(
                    "crypto_activity",
                    ["transaction_id", "blockchain", "wallet_address_from", "wallet_address_to", "tx_hash", "exchange_name"],
                    [
                        [q(tid), q(bc), q(wfrom), q(wto), q(txh), qn(exch)]
                        for (tid, bc, wfrom, wto, txh, exch) in self.crypto_rows
                    ],
                )
            )
        return "\n\n".join(blocks)

    def counts(self) -> dict:
        return {
            "total": len(self.tx_rows),
            "card": len(self.card_rows),
            "payment": len(self.payment_rows),
            "crypto": len(self.crypto_rows),
        }


# ============================================================================
# Background "everyday life" generator -- CARD and PAYMENT only, deliberately
# never CRYPTO and never cross-border, so it can never accidentally trip a
# rule. At most one row per calendar day (a day-by-day probability gate, not
# uniform-random placement), which also rules out an accidental same-day
# cluster large enough to look like a burst.
# ============================================================================

CARD_MERCHANTS = [
    # (name, mcc, low, high, card_present)
    ("Migros Zuerich Bahnhofstrasse", "5411", 12, 140, True),
    ("Coop Supermarkt Bern", "5411", 10, 130, True),
    ("Denner Basel City", "5411", 8, 90, True),
    ("SBB CFF FFS Ticketshop", "4111", 4, 60, True),
    ("Starbucks Zuerich HB", "5812", 5, 22, True),
    ("Restaurant Kronenhalle", "5812", 22, 95, True),
    ("Manor Geneve", "5311", 25, 260, True),
    ("Swisscom Shop Lausanne", "5732", 30, 450, True),
    ("Orell Fuessli Buchhandlung", "5942", 12, 70, True),
    ("Amavita Pharmacie", "5912", 6, 65, True),
    ("Coop Pronto Tankstelle Zuerich", "5541", 40, 110, True),
    ("Ricardo.ch Marketplace", "5999", 9, 180, False),
    ("Netflix.com", "4899", 15, 20, False),
    ("SWISS International Air Lines", "4511", 120, 780, False),
    ("Hotel Schweizerhof Bern", "7011", 140, 480, True),
]


def random_card_purchase(customer: Customer, days_ago):
    name, mcc, lo, hi, present = rng.choice(CARD_MERCHANTS)
    customer.card(days_ago, rng.uniform(lo, hi), "CHF", name, mcc, card_present=present)


def random_domestic_payment(customer: Customer, days_ago):
    payee, iban = rng.choice(customer.domestic_payees)
    customer.payment(days_ago, rng.uniform(50, 1800), "CHF", "SIC", iban, customer.country)


def gen_background(customer: Customer, start_day: int, end_day: int, daily_prob: float, payment_prob=0.18):
    """One pass over [end_day..start_day] days-ago; each day independently gets at most one row."""
    for day in range(end_day, start_day + 1):
        if rng.random() < daily_prob:
            days_ago = max(0.02, day + rng.uniform(-0.4, 0.4))
            if rng.random() < payment_prob:
                random_domestic_payment(customer, days_ago)
            else:
                random_card_purchase(customer, days_ago)


# ============================================================================
# risk_rules -- rendered from the seven Java rules' javadocs.
# ============================================================================

RISK_RULES = [
    dict(
        code="R-01",
        name="Near-Threshold Structuring",
        applies_to="ALL",
        logic=(
            "Three or more transactions in the trailing 7 days each sitting just under the "
            "10,000 reporting threshold, together totalling 10,000 or more. Channel-agnostic: "
            "the same behaviour shows up in cards, transfers and crypto alike."
        ),
        weight="44.00",
    ),
    dict(
        code="R-02",
        name="Elevated-Risk Payment Corridor",
        applies_to="PAYMENT",
        logic=(
            "A payment to a beneficiary bank in an elevated-risk jurisdiction (AF, HT, IR, KP, "
            "MM, SY or YE), where the customer has sent two or more such cross-border payments "
            "in the trailing 7 days, or their combined value in that window reaches 5,000 or more."
        ),
        weight="32.00",
    ),
    dict(
        code="R-03",
        name="Card-Not-Present Decline Cluster",
        applies_to="CARD",
        logic=(
            "Three or more card-not-present declines within a rolling 1-hour window, spread "
            "across two or more distinct merchants -- the pattern a stolen card number leaves "
            "while it is being validated."
        ),
        weight="52.00",
    ),
    dict(
        code="R-04",
        name="Rapid Exchange Disposal",
        applies_to="CRYPTO",
        logic=(
            "Crypto that arrived in a wallet is sent on to a known exchange within 1 hour, with "
            "10,000 or more having left that wallet over the trailing 24 hours."
        ),
        weight="34.00",
    ),
    dict(
        code="R-05",
        name="Flagged Wallet Proximity",
        applies_to="CRYPTO",
        logic=(
            "The sending wallet is within 2 hops of an address on the flagged-wallet watch "
            "list, found by a depth-limited walk of the wallet graph. Direct contact (0-1 hops) "
            "prices at the full weight; one intermediary wallet away (2 hops) prices at half."
        ),
        weight="54.00",
    ),
    dict(
        code="R-06",
        name="Dormancy Burst",
        applies_to="ALL",
        logic=(
            "An account silent for 90 days or more suddenly transacts 5 or more times within a "
            "single day -- the shape a taken-over or rented account leaves behind."
        ),
        weight="20.00",
    ),
    dict(
        code="R-07",
        name="Quasi-Cash Concentration",
        applies_to="CARD",
        logic=(
            "2,500 or more of a single day's card spend concentrated at quasi-cash, "
            "money-transfer or gambling merchants -- the card-side equivalent of a cash machine."
        ),
        weight="18.00",
    ),
]

RISK_RULES_COMMENT = """\
-- ============================================================================
-- risk_rules -- the seven codes the merged core (core/risk/rules) defines,
-- rule_code matching each Java class's Rule.code() exactly. threshold_logic is
-- each rule's javadoc condition restated as a sentence; weight is tuned against
-- RiskLevel's own bands (LOW at 0, MEDIUM at 25, HIGH at 50, CRITICAL at 75 --
-- see core/model/RiskLevel.java) so the arithmetic tells the story on its own:
--
--   - A single "primary" typology rule (structuring, elevated corridor, card
--     testing, rapid crypto disposal, direct flagged-wallet contact) lands on
--     its own in the MEDIUM-to-HIGH range: worth an operator's attention, not
--     yet a foregone conclusion.
--   - Two primary signals compounding on the SAME transaction -- exactly what
--     happens when a structuring payment also runs a high-risk corridor --
--     clears the CRITICAL floor (75). The compounding is the point: neither
--     rule alone is dispositive, together they are.
--   - The two "behavioural" rules (dormancy-then-burst, quasi-cash
--     concentration) are the lightest on purpose: each is real but weaker
--     alone (a returning traveller; a legitimate flutter at the casino), so a
--     single hit stays LOW/MEDIUM and it takes both firing on the same
--     transaction to clear into MEDIUM.
--   - R-05's weight below is the DIRECT-contact price. FlaggedWalletProximityRule
--     halves it for a 2-hop (one-intermediary) match in its own contribution()
--     override -- that scaling lives in Java, not in this table.
--
-- Worked totals from the demo customers below (all per-transaction, i.e. what
-- RiskScorer.score(...) totals for the rules that fire on one activity):
--   R-02 alone, Sandra Wyss's first two MM payments ................. 32.00  MEDIUM
--   R-01 + R-02 together, her third payment onward ................... 76.00  CRITICAL
--   R-03 alone, Thomas Egger's card-testing cluster ................... 52.00  HIGH
--   R-04 + R-05@2 hops, Julian Meier's exchange disposal ............... 61.00  HIGH
--   R-06 + R-07 together, Priya Nair's dormancy burst ................... 38.00  MEDIUM
-- ============================================================================"""


def render_risk_rules() -> str:
    rows = [
        [
            q(uid(f"risk_rule:{r['code']}")),
            q(r["code"]),
            q(r["name"]),
            q(r["applies_to"]),
            q(r["logic"]),
            r["weight"],
            "TRUE",
        ]
        for r in RISK_RULES
    ]
    return RISK_RULES_COMMENT + "\n" + insert(
        "risk_rules",
        ["rule_id", "rule_code", "rule_name", "applies_to", "threshold_logic", "weight", "enabled"],
        rows,
    )


# ============================================================================
# The six narrative customers, plus Kenji (wallet-graph plumbing only).
# ============================================================================


def build_livia() -> Customer:
    """Clean retail: ordinary Swiss spending, nothing flagged, ever. Proves LOW is reachable."""
    c = Customer("livia-baumann", "CH-7002-4471", "Livia Baumann", "CH", "RETAIL", onboarded_days_ago=1900)
    gen_background(c, start_day=90, end_day=1, daily_prob=0.79)
    return c


def build_reto() -> Customer:
    """Noisy but clean: same-merchant card-present declines (not CNP, one merchant -- exactly the
    'customer mistyping a PIN' case R-03's javadoc calls out) plus one unremarkable one-off
    cross-border payment to a non-listed country. Nothing here is close to any threshold; the
    point is that the system does not cry wolf at surface noise the way a human skim might."""
    c = Customer("reto-zimmermann", "CH-7002-4525", "Reto Zimmermann", "CH", "RETAIL", onboarded_days_ago=1200)
    gen_background(c, start_day=90, end_day=1, daily_prob=0.73)

    station = "Coop Pronto Tankstelle Aarau"
    for days_ago in (12.300, 12.286, 12.272):
        c.card(
            days_ago,
            d(rng.uniform(70, 95)),
            "CHF",
            station,
            "5541",
            card_present=True,
            status="FAILED",
            decline_reason="INSUFFICIENT_FUNDS",
        )
    c.payment(20.0, "3200.00", "CHF", "SEPA", de_iban(), "DE")
    return c


def build_sandra() -> Customer:
    """The centrepiece: five payments to the same Myanmar beneficiary, each just under the 10,000
    reporting threshold, across the last week. R-02 (elevated corridor) is visible from the very
    first payment -- a single 9,000+ transfer to a listed jurisdiction is already 'substantial'.
    R-01 (structuring) only lights up once the THIRD near-threshold payment lands within the
    trailing 7 days -- invisible in any one row, unmistakable once the third arrives. Both fire
    together from then on: 44.00 + 32.00 = 76.00, CRITICAL."""
    c = Customer("sandra-wyss", "CH-7002-4488", "Sandra Wyss", "CH", "RETAIL", onboarded_days_ago=800)
    gen_background(c, start_day=90, end_day=9, daily_prob=0.56)

    beneficiary = "008812345671"  # Myanmar has no IBAN registry entry; a local account number, not a fake IBAN
    payments = [
        (7.20, "9450.00"),
        (5.70, "9680.00"),
        (4.10, "9300.00"),
        (2.60, "9750.00"),
        (1.00, "9540.00"),
    ]
    total = sum(Decimal(a) for _, a in payments)
    assert all(Decimal("9000.00") <= Decimal(a) < Decimal("10000.00") for _, a in payments)
    assert total >= Decimal("10000.00"), total
    assert payments[-1][0] - payments[0][0] < 7, "all five must sit inside one rolling 7-day window"
    for days_ago, amount in payments:
        c.payment(days_ago, amount, "CHF", "SWIFT", beneficiary, "MM")
    return c


def build_thomas() -> Customer:
    """Card-testing fraud: four CNP declines in well under an hour, spread across three distinct
    online merchants, small stake amounts -- exactly what a stolen card number being validated
    looks like. R-03 alone: 52.00, HIGH -- urgent, block-the-card territory."""
    c = Customer("thomas-egger", "CH-7002-4518", "Thomas Egger", "CH", "RETAIL", onboarded_days_ago=400)
    gen_background(c, start_day=90, end_day=2, daily_prob=0.55)

    attempts = [
        (1.550, "QuickTopUp Mobile Recharge", "4814", "1.00", "INVALID_CVV"),
        (1.543, "GlobstarGiftCards.com", "5999", "5.00", "DO_NOT_HONOR"),
        (1.535, "StreamVault Plus Subscription", "5815", "9.99", "SUSPECTED_FRAUD"),
        (1.526, "QuickTopUp Mobile Recharge", "4814", "2.50", "INVALID_CVV"),
    ]
    assert (attempts[0][0] - attempts[-1][0]) * SECONDS_PER_DAY < 3600, "must fit inside one rolling hour"
    assert len({m for _, m, *_ in attempts}) >= 2
    for days_ago, merchant, mcc, amount, reason in attempts:
        c.card(days_ago, amount, "CHF", merchant, mcc, card_present=False, status="FAILED", decline_reason=reason)
    return c


def build_priya() -> Customer:
    """Dormant, then a burst: ordinary activity that stops completely more than 90 days before a
    single day where the account suddenly transacts six times, two of them at gambling / money
    transfer merchants totalling well past 2,500 -- the account-takeover shape. R-06 and R-07
    both fire from the fifth transaction of that day onward: 20.00 + 18.00 = 38.00, MEDIUM."""
    c = Customer("priya-nair", "CH-7002-4501", "Priya Nair", "CH", "RETAIL", onboarded_days_ago=2200)
    gen_background(c, start_day=135, end_day=100, daily_prob=0.42)

    burst = [
        (2.35, "Migros Zuerich Bahnhofstrasse", "5411", "45.30", True, None),
        (2.30, "SBB CFF FFS Ticketshop", "4111", "28.00", True, None),
        (2.25, "Starbucks Zuerich HB", "5812", "12.50", True, None),
        (2.20, "Coop Supermarkt Bern", "5411", "63.90", True, None),
        (2.15, "Bethard Casino Online", "7995", "2800.00", False, None),
        (2.10, "Western Union Bahnhofstrasse", "4829", "650.00", True, None),
    ]
    quasi_cash_total = sum(Decimal(a) for _, _, _, a, _, _ in burst[4:])
    assert quasi_cash_total >= Decimal("2500.00"), quasi_cash_total
    for days_ago, merchant, mcc, amount, present, _ in burst:
        c.card(days_ago, amount, "CHF", merchant, mcc, card_present=present)
    return c


# ---- the crypto narrative: Julian Meier, plus Kenji Watanabe for graph plumbing ----
#
# WALLET_CHAIN -- the genuine two-hop path core/graph's FlaggedWalletSearch walks (a DIRECTED
# graph: WalletGraph.neighborsOf(w) only follows w's own outgoing crypto_activity rows, so the
# chain has to be built sender-first, not just "a flagged address dropped in somewhere"):
#
#   Julian Meier's wallet  --[1500 USDT, ~4d ago]-->  Kenji Watanabe's wallet   (hop 1)
#   Kenji Watanabe's wallet --[3200 USDT, ~10d ago]--> the flagged wallet (WF) (hop 2)
#
# Both edges predate Julian's exchange-disposal transaction, so at the moment that transaction is
# scored, hopsToNearestFlaggedWallet({Julian's wallet}, {WF}, maxDepth=2) walks
# Julian -> Kenji -> WF and returns 2. FlaggedWalletProximityRule prices 2 hops at half weight.
#
# The flagged-wallet SET ITSELF (WF's address below) is not yet backed by any table or Java
# constant in this codebase -- FlaggedWalletSearch takes `flaggedWallets` as a parameter, the way
# ElevatedRiskJurisdictions.CODES is a constant for R-02 but nothing analogous exists yet for
# R-05. Whichever future layer supplies that set needs to include this exact address:
#
#   WALLET_ADDRESS_FLAGGED (see below, printed to stderr by this script too)
#
# Separately, and on the SAME transaction as the flagged-wallet check: funds arrive in Julian's
# wallet, and inside 25 minutes he sends 11,500 USDT on to Kraken -- rapid exchange disposal,
# R-04. R-04 (34.00) + R-05 at 2 hops (54.00 * 0.50 = 27.00) = 61.00, HIGH, both signals visible
# on one activity.

WALLET_ADDRESS_FUNDER = eth_address()
WALLET_ADDRESS_JULIAN = eth_address()
WALLET_ADDRESS_KENJI = eth_address()
WALLET_ADDRESS_FLAGGED = eth_address()
WALLET_ADDRESS_KRAKEN = eth_address()
WALLET_ADDRESS_JULIAN_COLD = btc_address()
WALLET_ADDRESS_JULIAN_SAVINGS = eth_address()
WALLET_ADDRESS_BITSTAMP = btc_address()


def build_kenji() -> Customer:
    """Minor supporting customer: unremarkable background activity, plus the one crypto_activity
    row that completes Julian's two-hop chain to the flagged wallet (see WALLET_CHAIN above)."""
    c = Customer("kenji-watanabe", "CH-7002-4495", "Kenji Watanabe", "CH", "RETAIL", onboarded_days_ago=1500)
    gen_background(c, start_day=90, end_day=1, daily_prob=0.20)
    c.crypto(
        10.0,
        "3200.00",
        "USDT",
        "ETHEREUM",
        WALLET_ADDRESS_KENJI,
        WALLET_ADDRESS_FLAGGED,
        eth_tx_hash(),
        tag="wf-edge",
    )
    return c


def build_julian() -> Customer:
    c = Customer("julian-meier", "CH-7002-4482", "Julian Meier", "CH", "PRIVATE", onboarded_days_ago=600)
    gen_background(c, start_day=90, end_day=1, daily_prob=0.43)

    # Hop 1 of the flagged-wallet chain: Julian -> Kenji (4 days ago, well before the disposal).
    c.crypto(
        4.0, "1500.00", "USDT", "ETHEREUM", WALLET_ADDRESS_JULIAN, WALLET_ADDRESS_KENJI, eth_tx_hash(), tag="hop1"
    )

    # Inbound funding, then rapid disposal to Kraken 25 minutes later.
    funding_days_ago = 2.10
    disposal_days_ago = funding_days_ago - (25 / 1440)
    assert (funding_days_ago - disposal_days_ago) * SECONDS_PER_DAY <= 3600
    c.crypto(
        funding_days_ago,
        "11800.00",
        "USDT",
        "ETHEREUM",
        WALLET_ADDRESS_FUNDER,
        WALLET_ADDRESS_JULIAN,
        eth_tx_hash(),
        tag="funding",
    )
    c.crypto(
        disposal_days_ago,
        "11500.00",
        "USDT",
        "ETHEREUM",
        WALLET_ADDRESS_JULIAN,
        WALLET_ADDRESS_KRAKEN,
        eth_tx_hash(),
        exchange_name="Kraken",
        tag="disposal",
    )

    # Unrelated, ordinary crypto habits -- different wallets, nowhere near the flagged chain or
    # the disposal thresholds, so they cannot dilute or confuse either signal above.
    c.crypto(
        45.0,
        "0.02000000",
        "BTC",
        "BITCOIN",
        WALLET_ADDRESS_BITSTAMP,
        WALLET_ADDRESS_JULIAN_COLD,
        btc_tx_hash(),
        exchange_name="Bitstamp",
        tag="filler-buy",
    )
    c.crypto(
        30.0,
        "1200.00",
        "USDT",
        "ETHEREUM",
        WALLET_ADDRESS_JULIAN,
        WALLET_ADDRESS_JULIAN_SAVINGS,
        eth_tx_hash(),
        tag="filler-transfer",
    )
    return c


# ============================================================================
# Assembly
# ============================================================================


def main() -> None:
    header = f'''\
-- gotthard-spring -- demo dataset: risk rules + narrative customers.
--
-- Generated by scripts/generate_demo_seed_data.py, seed={SEED}. Do not hand-edit
-- this file -- change the generator and regenerate:
--
--     python3 scripts/generate_demo_seed_data.py \\
--         > backend/src/main/resources/db/migration/V4__seed_demo_data.sql
--
-- Every timestamp below is `now() - interval '<n> seconds'`, resolved once per
-- migration run, so the dataset is always "the last ~90 days ending near the
-- present" no matter when this migration actually applies -- see the module
-- docstring in the generator for why.
--
-- Seven customers. Six are the demo:
--   Livia Baumann     CH-7002-4471  clean retail                    -- LOW is reachable
--   Reto Zimmermann   CH-7002-4525  noisy but clean                 -- surface noise, no rule fires
--   Sandra Wyss       CH-7002-4488  structuring (the centrepiece)   -- MEDIUM building to CRITICAL
--   Thomas Egger      CH-7002-4518  card-testing fraud              -- HIGH, one rule, unmistakable
--   Priya Nair        CH-7002-4501  dormancy then a burst           -- MEDIUM, two signals together
--   Julian Meier      CH-7002-4482  crypto: flagged wallet + rapid disposal -- HIGH
-- Kenji Watanabe (CH-7002-4495) is not a story -- see the WALLET_CHAIN comment
-- near build_kenji()/build_julian() below for why he exists.
'''

    customers = [
        (build_livia(), "clean retail customer"),
        (build_reto(), "noisy but clean: declines and a foreign payment that trip nothing"),
        (build_sandra(), "structuring centrepiece: near-threshold payments to Myanmar"),
        (build_thomas(), "card-testing fraud: a CNP decline cluster"),
        (build_priya(), "dormant account waking with a burst"),
        (build_kenji(), "background only -- completes Julian's flagged-wallet chain"),
        (build_julian(), "crypto: two hops from a flagged wallet, plus rapid exchange disposal"),
    ]

    parts = [header, render_risk_rules()]
    for customer, narrative in customers:
        parts.append(customer.render(narrative))

    print("\n\n".join(parts))

    total = sum(c.counts()["total"] for c, _ in customers)
    print("-- seed summary (generation-time counts, informational only):", file=sys.stderr)
    for customer, narrative in customers:
        counts = customer.counts()
        print(
            f"--   {customer.full_name:<18} {customer.reference}  total={counts['total']:>3}"
            f"  card={counts['card']:>3} payment={counts['payment']:>3} crypto={counts['crypto']:>2}"
            f"  -- {narrative}",
            file=sys.stderr,
        )
    print(f"--   TOTAL transactions: {total}", file=sys.stderr)
    print(f"--   Flagged wallet address (WF): {WALLET_ADDRESS_FLAGGED}", file=sys.stderr)
    print(f"--   Julian's wallet:             {WALLET_ADDRESS_JULIAN}", file=sys.stderr)
    print(f"--   Kenji's wallet (1-hop):       {WALLET_ADDRESS_KENJI}", file=sys.stderr)


if __name__ == "__main__":
    main()
