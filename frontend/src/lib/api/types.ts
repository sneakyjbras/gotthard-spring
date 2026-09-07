/**
 * Domain types for the operator console.
 *
 * Field names mirror the JPA entities in
 * `backend/src/main/java/ch/gotthard/domain/model/` exactly (camelCase, same
 * names) so that wiring a real `fetch` client later is a matter of typing,
 * not renaming. Where a type here is a *projection* rather than a 1:1 copy
 * of an entity — flattening a `@ManyToOne` to its id, or a list endpoint
 * returning a narrower shape than the full record — that is called out on
 * the type itself, the same distinction the backend draws between an entity
 * and an `api/` DTO.
 */

export type UUID = string;

/** ISO-8601 with offset or `Z`, e.g. `"2024-03-11T09:00:00Z"` — matches `OffsetDateTime`. */
export type IsoDateTime = string;

/** Mirrors `RiskLevel` — the scale shared by a rule-computed and an AI-assessed level. */
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export const RISK_LEVELS: readonly RiskLevel[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

/** Mirrors `OperatorRole`. */
export type OperatorRole = 'OPERATOR' | 'SUPERVISOR';

/** Mirrors the `transactions.activity_type` discriminator. */
export type ActivityType = 'CARD' | 'PAYMENT' | 'CRYPTO';

/** Mirrors `TransactionStatus`. */
export type TransactionStatus = 'COMPLETED' | 'PENDING' | 'FAILED' | 'REVERSED';

/** Mirrors `Operator`. */
export interface Operator {
  operatorId: UUID;
  username: string;
  displayName: string;
  role: OperatorRole;
}

/** Mirrors `Customer`. */
export interface Customer {
  customerId: UUID;
  /** Human-typeable identifier, e.g. `"CH-4410-8821"`. Operators search by this or by name. */
  reference: string;
  fullName: string;
  /** ISO 3166-1 alpha-2. */
  country: string;
  segment: string;
  onboardedAt: IsoDateTime;
}

/**
 * What customer search actually returns: a `Customer` projection plus the
 * risk signal an operator scans the results for.
 *
 * `latestRiskLevel` is not a column on `customers` — the schema has no risk
 * field there on purpose (risk lives on `ai_analyses`, one row per
 * analysis). This is the level from that customer's most recent analysis,
 * `null` until a first analysis has run. Modelled as its own type rather
 * than tacked onto `Customer` because a search-result projection and the
 * full entity are different contracts, even though most fields overlap.
 */
export interface CustomerSummary extends Customer {
  latestRiskLevel: RiskLevel | null;
}

interface TransactionBase {
  transactionId: UUID;
  /** Flattened from the entity's `@ManyToOne Customer` — a list/detail DTO deals in ids. */
  customerId: UUID;
  amount: number;
  /** ISO 4217. */
  currency: string;
  status: TransactionStatus;
  createdAt: IsoDateTime;
}

/** Mirrors `CardActivity`, the `CARD` branch of the `Transaction` hierarchy. */
export interface CardTransaction extends TransactionBase {
  activityType: 'CARD';
  cardPan: string;
  cardType: string;
  merchantName: string;
  mccCode: string;
  cardPresent: boolean;
  authorizationCode: string | null;
  declineReason: string | null;
}

/** Mirrors `PaymentActivity`, the `PAYMENT` branch. */
export interface PaymentTransaction extends TransactionBase {
  activityType: 'PAYMENT';
  paymentMethod: string;
  senderAccount: string;
  receiverAccount: string;
  /** ISO 3166-1 alpha-2. */
  receiverBankCountry: string;
}

/** Mirrors `CryptoActivity`, the `CRYPTO` branch. */
export interface CryptoTransaction extends TransactionBase {
  activityType: 'CRYPTO';
  blockchain: string;
  walletAddressFrom: string;
  walletAddressTo: string;
  txHash: string;
  exchangeName: string | null;
}

/**
 * Mirrors the `Transaction` JOINED-inheritance hierarchy as a discriminated
 * union — `activityType` is the discriminant, the same role `activity_type`
 * plays as the JPA `@DiscriminatorColumn`.
 */
export type Transaction = CardTransaction | PaymentTransaction | CryptoTransaction;

export interface LoginRequest {
  username: string;
  password: string;
}

/**
 * Mirrors `Money`. Card, payment and crypto amounts stay in the currency the
 * transaction actually happened in; only channel and window totals are
 * converted to the reporting currency (CHF) — see `ReportingRates` on the
 * backend.
 */
export interface Money {
  currency: string;
  amount: number;
}

/** An optional `[from, to]` bound for the activity and risk endpoints. Omitted ends default server-side. */
export interface ActivityWindowParams {
  from?: IsoDateTime;
  to?: IsoDateTime;
}

/** Mirrors `ChannelActivity` — one channel's totals for the window. */
export interface ChannelSummary {
  channel: ActivityType;
  transactionCount: number;
  volume: Money;
  unsuccessfulCount: number;
  firstAt: IsoDateTime;
  lastAt: IsoDateTime;
}

/**
 * One row of `GET /api/customers/{id}/activity`'s recent-transactions page —
 * mirrors `TransactionView` exactly.
 *
 * Unlike {@link Transaction} below (the richer per-channel shape the
 * styleguide's mock fixture uses), the real activity endpoint always returns
 * this flattened row: `counterparty` and `channelDetail` carry a different
 * meaning per channel rather than each channel exposing its own named
 * fields — `counterparty` is the merchant name (CARD), the beneficiary
 * account (PAYMENT) or the receiving wallet address (CRYPTO);
 * `channelDetail` is the MCC, the beneficiary bank country, or the
 * blockchain, respectively. A page renders each channel's own columns by
 * relabelling these two fields per `channel`, not by expecting extra ones.
 */
export interface ActivityTransaction {
  transactionId: UUID;
  channel: ActivityType;
  amount: Money;
  status: TransactionStatus;
  occurredAt: IsoDateTime;
  counterparty: string;
  channelDetail: string | null;
}

/** Mirrors `ActivityOverview` — the customer detail screen's activity half. */
export interface ActivityOverview {
  customer: Customer;
  from: IsoDateTime;
  to: IsoDateTime;
  transactionCount: number;
  totalVolume: Money;
  unsuccessfulCount: number;
  /** Only the channels the customer actually used in the window. */
  channels: ChannelSummary[];
  /** The newest transactions in the window, newest first, capped server-side. */
  recentTransactions: ActivityTransaction[];
}

/**
 * Mirrors `RiskFinding` — one rule firing on one transaction. `condition` is the
 * rule's own `threshold_logic` from the database, so the sentence explaining why
 * a rule fired lives in one place rather than being restated here.
 */
export interface RiskFinding {
  transactionId: UUID;
  occurredAt: IsoDateTime;
  channel: ActivityType;
  ruleCode: string;
  ruleName: string;
  condition: string;
  contribution: number;
}

/**
 * Mirrors `CustomerRiskReport`. `score` is the highest any single
 * transaction in the window scored, not a sum across it — see the backend
 * type's own doc for why.
 */
export interface CustomerRiskReport {
  customer: Customer;
  from: IsoDateTime;
  to: IsoDateTime;
  transactionsEvaluated: number;
  score: number;
  level: RiskLevel;
  findings: RiskFinding[];
}

/** Mirrors `OperatorRef` — who an analysis is attributed to, as history and detail read it. */
export interface AnalysisOperator {
  operatorId: UUID;
  username: string;
  displayName: string;
}

/**
 * Mirrors `PolicyCitationView` — one policy chunk the model was shown, best match first.
 * Every retrieved chunk is cited, whether or not the summary refers to it — the list answers "what
 * was this written from", not "what did the model quote".
 */
export interface AnalysisCitation {
  chunkId: UUID;
  document: string;
  title: string;
  section: string;
  body: string;
  similarity: number;
  rank: number;
}

/**
 * Mirrors `AiAnalysisView` — one row of `ai_analyses` as an operator reads it.
 *
 * `computedLevel` is what the rules decided; `assessedLevel` is the model's own independent read;
 * `levelsDiverged` is read back from PostgreSQL's generated column, not recomputed on this side — see
 * the backend type's own doc. `provider` and `model` are what actually produced this row
 * (`"anthropic"`/`"claude-opus-5"`, or `"stub"`/`"stub-analyst-v1"` with no API key configured) and
 * must always be shown next to the verdict, not just logged.
 *
 * `citations` is empty on a history listing (`getAnalysisHistory`) — that call answers "what has been
 * asked about this customer", not "what policy backed each one". `getAnalysis` and a freshly run
 * `runAnalysis` both fill it in.
 */
export interface AiAnalysis {
  analysisId: UUID;
  customer: Customer;
  requestedBy: AnalysisOperator;
  createdAt: IsoDateTime;
  windowFrom: IsoDateTime;
  windowTo: IsoDateTime;
  computedScore: number;
  computedLevel: RiskLevel;
  assessedLevel: RiskLevel;
  levelsDiverged: boolean;
  summary: string;
  recommendations: string[];
  provider: string;
  model: string;
  promptVersion: string;
  /** `null` when the provider does not report usage. */
  inputTokens: number | null;
  outputTokens: number | null;
  latencyMs: number | null;
  citations: AnalysisCitation[];
}
