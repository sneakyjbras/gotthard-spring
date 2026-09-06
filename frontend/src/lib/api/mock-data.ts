import type {
  ActivityOverview,
  ActivityTransaction,
  ActivityType,
  ChannelSummary,
  CustomerRiskReport,
  CustomerSummary,
  Money,
  RiskFinding,
  RiskLevel,
  Transaction,
  TransactionStatus,
} from './types';

/**
 * Seed data standing in for the backend, which does not expose an API yet.
 * Shapes match `./types` exactly, so nothing here changes when a real
 * `httpApiClient` replaces `mockApiClient` in `./client`.
 */

const id = (): string => crypto.randomUUID();

export const mockCustomers: readonly CustomerSummary[] = [
  {
    customerId: id(),
    reference: 'CH-4410-8821',
    fullName: 'Elena Fischer',
    country: 'CH',
    segment: 'PRIVATE_BANKING',
    onboardedAt: '2019-03-11T09:00:00Z',
    latestRiskLevel: 'LOW',
  },
  {
    customerId: id(),
    reference: 'CH-2201-4457',
    fullName: 'Marco Bianchi',
    country: 'IT',
    segment: 'RETAIL',
    onboardedAt: '2021-07-22T14:30:00Z',
    latestRiskLevel: 'MEDIUM',
  },
  {
    customerId: id(),
    reference: 'CH-7734-1092',
    fullName: 'Amélie Rousseau',
    country: 'FR',
    segment: 'WEALTH',
    onboardedAt: '2017-11-04T08:15:00Z',
    latestRiskLevel: 'LOW',
  },
  {
    customerId: id(),
    reference: 'CH-5583-2201',
    fullName: 'James Whitfield',
    country: 'GB',
    segment: 'CORPORATE',
    onboardedAt: '2022-01-18T11:45:00Z',
    latestRiskLevel: 'HIGH',
  },
  {
    customerId: id(),
    reference: 'CH-1049-7734',
    fullName: 'Lukas Meier',
    country: 'CH',
    segment: 'RETAIL',
    onboardedAt: '2023-05-09T16:20:00Z',
    latestRiskLevel: 'LOW',
  },
  {
    customerId: id(),
    reference: 'CH-8827-3345',
    fullName: 'Fatima Al-Mansoori',
    country: 'AE',
    segment: 'PRIVATE_BANKING',
    onboardedAt: '2020-09-30T10:00:00Z',
    latestRiskLevel: 'MEDIUM',
  },
  {
    customerId: id(),
    reference: 'CH-3391-6620',
    fullName: 'Wei Chen',
    country: 'SG',
    segment: 'WEALTH',
    onboardedAt: '2018-02-14T07:50:00Z',
    latestRiskLevel: 'CRITICAL',
  },
  {
    customerId: id(),
    reference: 'CH-6045-1183',
    fullName: 'Noah Bergström',
    country: 'SE',
    segment: 'RETAIL',
    onboardedAt: '2024-04-01T13:10:00Z',
    latestRiskLevel: null,
  },
  {
    customerId: id(),
    reference: 'CH-2277-9904',
    fullName: 'Priya Nair',
    country: 'IN',
    segment: 'CORPORATE',
    onboardedAt: '2021-12-05T09:40:00Z',
    latestRiskLevel: 'LOW',
  },
  {
    customerId: id(),
    reference: 'CH-9012-4456',
    fullName: 'Daniel Okafor',
    country: 'NG',
    segment: 'RETAIL',
    onboardedAt: '2022-08-19T15:05:00Z',
    latestRiskLevel: 'MEDIUM',
  },
  {
    customerId: id(),
    reference: 'CH-4456-2201',
    fullName: 'Sophie Dubois',
    country: 'FR',
    segment: 'PRIVATE_BANKING',
    onboardedAt: '2023-10-27T12:25:00Z',
    latestRiskLevel: null,
  },
  {
    customerId: id(),
    reference: 'CH-7789-3320',
    fullName: 'Hans Zimmermann',
    country: 'CH',
    segment: 'CORPORATE',
    onboardedAt: '2016-06-13T08:00:00Z',
    latestRiskLevel: 'LOW',
  },
  {
    customerId: id(),
    reference: 'CH-1123-6654',
    fullName: 'Isabella Conti',
    country: 'IT',
    segment: 'WEALTH',
    onboardedAt: '2019-09-08T10:30:00Z',
    latestRiskLevel: 'HIGH',
  },
  {
    customerId: id(),
    reference: 'CH-5567-8891',
    fullName: 'Robert Keller',
    country: 'US',
    segment: 'RETAIL',
    onboardedAt: '2022-03-16T17:15:00Z',
    latestRiskLevel: 'MEDIUM',
  },
  {
    customerId: id(),
    reference: 'CH-3320-1145',
    fullName: 'Anna Kowalski',
    country: 'PL',
    segment: 'RETAIL',
    onboardedAt: '2024-01-22T09:55:00Z',
    latestRiskLevel: 'LOW',
  },
  {
    customerId: id(),
    reference: 'CH-8845-2267',
    fullName: 'Yuki Tanaka',
    country: 'JP',
    segment: 'WEALTH',
    onboardedAt: '2020-11-11T06:40:00Z',
    latestRiskLevel: null,
  },
  {
    customerId: id(),
    reference: 'CH-6612-9934',
    fullName: 'Karim Haddad',
    country: 'AE',
    segment: 'CORPORATE',
    onboardedAt: '2018-07-25T14:00:00Z',
    latestRiskLevel: 'CRITICAL',
  },
  {
    customerId: id(),
    reference: 'CH-2245-7781',
    fullName: 'Charlotte Meyer',
    country: 'CH',
    segment: 'PRIVATE_BANKING',
    onboardedAt: '2021-04-30T11:20:00Z',
    latestRiskLevel: 'MEDIUM',
  },
];

const forCustomer = (index: number): string => mockCustomers[index % mockCustomers.length].customerId;

/**
 * A handful of rows spanning all three activity types, used by the
 * styleguide's DataTable example. Deliberately includes an IBAN, a masked
 * PAN and a wallet hash side by side with plain amounts — the surface
 * where "every number, ID, IBAN, wallet address and hash renders in
 * tabular monospace" is meant to be checked at a glance.
 */
export const mockTransactions: readonly Transaction[] = [
  {
    transactionId: id(),
    customerId: forCustomer(0),
    amount: 128.5,
    currency: 'CHF',
    status: 'COMPLETED',
    createdAt: '2026-08-29T18:42:11Z',
    activityType: 'CARD',
    cardPan: '4539771144714471',
    cardType: 'DEBIT',
    merchantName: 'Läderach Chocolatier',
    mccCode: '5411',
    cardPresent: true,
    authorizationCode: '881204',
    declineReason: null,
  },
  {
    transactionId: id(),
    customerId: forCustomer(3),
    amount: 45000,
    currency: 'EUR',
    status: 'COMPLETED',
    createdAt: '2026-08-28T09:03:47Z',
    activityType: 'PAYMENT',
    paymentMethod: 'WIRE',
    senderAccount: 'CH9300762011623852957',
    receiverAccount: 'DE89370400440532013000',
    receiverBankCountry: 'DE',
  },
  {
    transactionId: id(),
    customerId: forCustomer(6),
    amount: 2.184,
    currency: 'BTC',
    status: 'COMPLETED',
    createdAt: '2026-08-27T22:15:03Z',
    activityType: 'CRYPTO',
    blockchain: 'BITCOIN',
    walletAddressFrom: 'bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh',
    walletAddressTo: 'bc1q9d4ywgfnd8h4590yhpq0hz2xllc5vzc6mgpp4p',
    txHash: '4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33',
    exchangeName: 'Kraken',
  },
  {
    transactionId: id(),
    customerId: forCustomer(12),
    amount: 340,
    currency: 'CHF',
    status: 'FAILED',
    createdAt: '2026-08-27T13:51:29Z',
    activityType: 'CARD',
    cardPan: '5500005555555559',
    cardType: 'CREDIT',
    merchantName: 'Interflora Genève',
    mccCode: '5992',
    cardPresent: false,
    authorizationCode: null,
    declineReason: 'INSUFFICIENT_FUNDS',
  },
  {
    transactionId: id(),
    customerId: forCustomer(6),
    amount: 18.42,
    currency: 'ETH',
    status: 'COMPLETED',
    createdAt: '2026-08-26T04:08:56Z',
    activityType: 'CRYPTO',
    blockchain: 'ETHEREUM',
    walletAddressFrom: '0x71c7656ec7ab88b098defb751b7401b5f6d8976',
    walletAddressTo: '0x2b5634c42055806a59e9107ed44d43c426e58f9',
    txHash: '0x88df016429689c079f3b2f6ad39fa052532c56795b733da78a91ebe6a713944',
    exchangeName: null,
  },
  {
    transactionId: id(),
    customerId: forCustomer(17),
    amount: 12500,
    currency: 'USD',
    status: 'PENDING',
    createdAt: '2026-08-25T19:27:40Z',
    activityType: 'PAYMENT',
    paymentMethod: 'SEPA',
    senderAccount: 'AE070331234567890123456',
    receiverAccount: 'CH5604835012345678009',
    receiverBankCountry: 'CH',
  },
  {
    transactionId: id(),
    customerId: forCustomer(1),
    amount: 76.9,
    currency: 'CHF',
    status: 'REVERSED',
    createdAt: '2026-08-24T08:12:05Z',
    activityType: 'CARD',
    cardPan: '4916338506082832',
    cardType: 'DEBIT',
    merchantName: 'SBB CFF FFS',
    mccCode: '4112',
    cardPresent: true,
    authorizationCode: '552011',
    declineReason: null,
  },
];

/**
 * Deterministic activity and risk data for `mockApiClient`'s
 * `getCustomerActivity`/`getCustomerRisk` — the shapes the real
 * `GET /api/customers/{id}/activity` and `/risk` endpoints return (see
 * `./types`), synthesised per customer rather than hand-written so all
 * eighteen `mockCustomers` have something to show, not just a hand-picked
 * few. Seeded off `customerId`, so the same customer always gets the same
 * mock activity across reloads and re-searches within one session.
 */

/** A tiny seeded PRNG (mulberry32) — deterministic per customer, no dependency. */
function mulberry32(seed: number): () => number {
  let state = seed;
  return () => {
    state |= 0;
    state = (state + 0x6d2b79f5) | 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function seedOf(text: string): number {
  let hash = 0;
  for (let index = 0; index < text.length; index += 1) {
    hash = (Math.imul(31, hash) + text.charCodeAt(index)) | 0;
  }
  return hash;
}

function pick<T>(rand: () => number, options: readonly T[]): T {
  return options[Math.floor(rand() * options.length)];
}
const between = (rand: () => number, min: number, max: number): number => min + rand() * (max - min);
const round2 = (value: number): number => Math.round(value * 100) / 100;

const CARD_MERCHANTS = ['Läderach Chocolatier', 'Coop Supermarché', 'SBB CFF FFS', 'Interflora Genève', 'Migros'] as const;
const MCC_CODES = ['5411', '5812', '4112', '5992', '5999'] as const;
const PAYMENT_COUNTRIES = ['DE', 'FR', 'IT', 'GB', 'US'] as const;
const CRYPTO_CHAINS = ['BITCOIN', 'ETHEREUM'] as const;
const STATUSES: readonly TransactionStatus[] = ['COMPLETED', 'COMPLETED', 'COMPLETED', 'PENDING', 'FAILED'];

const CHANNEL_CURRENCY: Record<ActivityType, () => string> = {
  CARD: () => 'CHF',
  PAYMENT: () => 'EUR',
  CRYPTO: () => 'BTC',
};

function channelCounterparty(rand: () => number, channel: ActivityType): { counterparty: string; channelDetail: string } {
  switch (channel) {
    case 'CARD':
      return { counterparty: pick(rand, CARD_MERCHANTS), channelDetail: pick(rand, MCC_CODES) };
    case 'PAYMENT':
      return { counterparty: `CH93${Math.floor(between(rand, 1e14, 9e14))}`, channelDetail: pick(rand, PAYMENT_COUNTRIES) };
    case 'CRYPTO':
      return { counterparty: `bc1q${Math.floor(between(rand, 1e12, 9e12)).toString(36)}`, channelDetail: pick(rand, CRYPTO_CHAINS) };
  }
}

function mockTransaction(rand: () => number, channel: ActivityType, hoursAgo: number): ActivityTransaction {
  const currency = CHANNEL_CURRENCY[channel]();
  const amount = channel === 'CRYPTO' ? round2(between(rand, 0.01, 3)) : round2(between(rand, 15, 4800));
  return {
    transactionId: id(),
    channel,
    amount: { currency, amount },
    status: pick(rand, STATUSES),
    occurredAt: new Date(Date.now() - hoursAgo * 3_600_000).toISOString(),
    ...channelCounterparty(rand, channel),
  };
}

function mockChannel(channel: ActivityType, transactions: readonly ActivityTransaction[]): ChannelSummary {
  const own = transactions.filter((transaction) => transaction.channel === channel);
  const volume: Money = {
    currency: 'CHF',
    amount: round2(own.reduce((sum, transaction) => sum + transaction.amount.amount, 0)),
  };
  return {
    channel,
    transactionCount: own.length,
    volume,
    unsuccessfulCount: own.filter((transaction) => transaction.status === 'FAILED' || transaction.status === 'REVERSED').length,
    firstAt: own[own.length - 1]?.occurredAt ?? new Date().toISOString(),
    lastAt: own[0]?.occurredAt ?? new Date().toISOString(),
  };
}

/** Stands in for `GET /api/customers/{id}/activity`. */
export function mockActivityOverview(customer: CustomerSummary): ActivityOverview {
  const rand = mulberry32(seedOf(customer.customerId));
  const channelsUsed: ActivityType[] = (['CARD', 'PAYMENT', 'CRYPTO'] as const).filter(() => rand() > 0.15);
  const transactions = channelsUsed
    .flatMap((channel) => {
      const count = Math.floor(between(rand, 3, 9));
      return Array.from({ length: count }, (_unused, index) => mockTransaction(rand, channel, index * between(rand, 4, 30)));
    })
    .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt));

  const channels = channelsUsed.map((channel) => mockChannel(channel, transactions));
  const now = new Date();
  return {
    customer,
    from: new Date(now.getTime() - 30 * 86_400_000).toISOString(),
    to: now.toISOString(),
    transactionCount: transactions.length,
    totalVolume: { currency: 'CHF', amount: round2(channels.reduce((sum, channel) => sum + channel.volume.amount, 0)) },
    unsuccessfulCount: channels.reduce((sum, channel) => sum + channel.unsuccessfulCount, 0),
    channels,
    recentTransactions: transactions,
  };
}

/**
 * The findings a risk level "would have" produced, worked from the same
 * per-transaction totals `V4__seed_demo_data.sql` documents (see
 * `./rule-catalogue` for the condition text these codes resolve to).
 */
const FINDINGS_BY_LEVEL: Record<Exclude<RiskLevel, 'LOW'>, readonly { ruleCode: string; ruleName: string; contribution: number }[]> = {
  MEDIUM: [{ ruleCode: 'R-02', ruleName: 'Elevated-Risk Payment Corridor', contribution: 32 }],
  HIGH: [{ ruleCode: 'R-03', ruleName: 'Card-Not-Present Decline Cluster', contribution: 52 }],
  CRITICAL: [
    { ruleCode: 'R-01', ruleName: 'Near-Threshold Structuring', contribution: 44 },
    { ruleCode: 'R-02', ruleName: 'Elevated-Risk Payment Corridor', contribution: 32 },
  ],
};

/** Stands in for `GET /api/customers/{id}/risk`. `latestRiskLevel` drives which findings "fired". */
export function mockRiskReport(customer: CustomerSummary): CustomerRiskReport {
  const rand = mulberry32(seedOf(customer.customerId) ^ 0x5eed);
  const level = customer.latestRiskLevel ?? 'LOW';
  const templates = level === 'LOW' ? [] : FINDINGS_BY_LEVEL[level];
  const channels: ActivityType[] = ['PAYMENT', 'CARD', 'CRYPTO'];
  const findings: RiskFinding[] = templates.map((template, index) => ({
    transactionId: id(),
    occurredAt: new Date(Date.now() - between(rand, 1, 96) * 3_600_000).toISOString(),
    channel: channels[index % channels.length],
    ruleCode: template.ruleCode,
    ruleName: template.ruleName,
    contribution: template.contribution,
  }));
  const now = new Date();
  return {
    customer,
    from: new Date(now.getTime() - 30 * 86_400_000).toISOString(),
    to: now.toISOString(),
    transactionsEvaluated: Math.floor(between(rand, 8, 40)),
    score: findings.reduce((sum, finding) => sum + finding.contribution, 0),
    level,
    findings,
  };
}
