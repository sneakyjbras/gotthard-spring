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

export interface Session {
  operator: Operator;
  token: string;
  issuedAt: IsoDateTime;
}
