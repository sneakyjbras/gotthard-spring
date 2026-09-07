import { useMemo, type ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import { AnalysisSection } from '@/components/analysis';
import { PageShell } from '@/components/layout';
import { DataTable, labelClasses, RiskBadge, StatBlock, type DataTableColumn } from '@/components/primitives';
import { api } from '@/lib/api/client';
import type {
  ActivityOverview,
  ActivityTransaction,
  ActivityType,
  ChannelSummary,
  Customer,
  CustomerRiskReport,
  RiskFinding,
} from '@/lib/api/types';
import { useAsync, type AsyncState } from '@/lib/hooks/useAsync';
import { formatAmount, formatDate, formatIban, truncateMiddle } from '@/lib/utils/format';

const CHANNEL_ORDER: readonly ActivityType[] = ['CARD', 'PAYMENT', 'CRYPTO'];
const CHANNEL_LABELS: Record<ActivityType, string> = { CARD: 'Card', PAYMENT: 'Payment', CRYPTO: 'Crypto' };

/**
 * The real activity endpoint returns one flattened row shape for every
 * channel (`ActivityTransaction`, see `./types`) — `counterparty` and
 * `channelDetail` carry a different meaning depending on `channel`. These
 * two maps are that relabelling: each channel's table gets its own headers
 * instead of a "counterparty / detail" lowest common denominator.
 */
const COUNTERPARTY_HEADERS: Record<ActivityType, string> = {
  CARD: 'Merchant',
  PAYMENT: 'Beneficiary account',
  CRYPTO: 'Receiving wallet',
};
const DETAIL_HEADERS: Record<ActivityType, string> = { CARD: 'MCC', PAYMENT: 'Bank country', CRYPTO: 'Blockchain' };

function counterpartyDisplay(channel: ActivityType, counterparty: string): string {
  if (channel === 'PAYMENT') return formatIban(counterparty);
  if (channel === 'CRYPTO') return truncateMiddle(counterparty, 8, 6);
  return counterparty;
}

function channelColumns(channel: ActivityType): DataTableColumn<ActivityTransaction>[] {
  return [
    { key: 'date', header: 'Date', monospace: true, render: (transaction) => formatDate(transaction.occurredAt) },
    {
      key: 'counterparty',
      header: COUNTERPARTY_HEADERS[channel],
      monospace: channel !== 'CARD',
      render: (transaction) => counterpartyDisplay(channel, transaction.counterparty),
    },
    {
      key: 'detail',
      header: DETAIL_HEADERS[channel],
      monospace: true,
      render: (transaction) => transaction.channelDetail ?? '—',
    },
    {
      key: 'amount',
      header: 'Amount',
      numeric: true,
      render: (transaction) => formatAmount(transaction.amount.amount, transaction.amount.currency),
    },
    { key: 'status', header: 'Status', render: (transaction) => transaction.status },
  ];
}

function StatusPanel({ children }: { children: ReactNode }) {
  return (
    <div className="border-t border-rule py-16 text-center">
      <p className="text-sm text-ink-faint">{children}</p>
    </div>
  );
}

function ErrorPanel({ message }: { message: string }) {
  return (
    <div className="border-t border-rule py-10">
      <p role="alert" className="text-sm text-accent-fg">
        {message}
      </p>
    </div>
  );
}

function IdentityField({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-1">
      <span className={labelClasses}>{label}</span>
      <span className="font-mono text-base text-ink">{value}</span>
    </div>
  );
}

function CustomerIdentity({ customer }: { customer: Customer }) {
  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-2">
        <p className={labelClasses}>Customer</p>
        <h1 className="text-2xl font-bold">{customer.fullName}</h1>
      </div>
      <div className="grid grid-cols-2 gap-6 sm:grid-cols-4">
        <IdentityField label="Reference" value={customer.reference} />
        <IdentityField label="Country" value={customer.country} />
        <IdentityField label="Segment" value={customer.segment.replace('_', ' ')} />
        <IdentityField label="Onboarded" value={formatDate(customer.onboardedAt)} />
      </div>
    </div>
  );
}

function ChannelSection({
  channel,
  summary,
  transactions,
}: {
  channel: ActivityType;
  summary: ChannelSummary;
  transactions: readonly ActivityTransaction[];
}) {
  const columns = useMemo(() => channelColumns(channel), [channel]);
  const rows = useMemo(
    () => transactions.filter((transaction) => transaction.channel === channel),
    [transactions, channel],
  );
  return (
    <div className="flex flex-col gap-6 border-t border-rule pt-10">
      <div className="flex flex-wrap items-end justify-between gap-6">
        <h3 className="text-lg font-bold">{CHANNEL_LABELS[channel]}</h3>
        <div className="flex flex-wrap gap-8">
          <StatBlock value={summary.transactionCount} label="Transactions" />
          <StatBlock value={formatAmount(summary.volume.amount, summary.volume.currency)} label="Volume" />
          <StatBlock value={summary.unsuccessfulCount} label="Unsuccessful" />
        </div>
      </div>
      <DataTable
        columns={columns}
        rows={rows}
        getRowKey={(transaction) => transaction.transactionId}
        emptyMessage={`No ${CHANNEL_LABELS[channel].toLowerCase()} transactions among the most recent activity shown below.`}
      />
    </div>
  );
}

function ActivityContent({ activity }: { activity: ActivityOverview }) {
  const activeChannels = CHANNEL_ORDER.map((channel) => ({
    channel,
    summary: activity.channels.find((candidate) => candidate.channel === channel),
  })).filter((entry): entry is { channel: ActivityType; summary: ChannelSummary } => entry.summary !== undefined);

  return (
    <div className="flex flex-col gap-10">
      <p className="text-sm text-ink-faint">
        {formatDate(activity.from)} – {formatDate(activity.to)}
      </p>
      <div className="grid grid-cols-2 gap-8 sm:grid-cols-3">
        <StatBlock value={activity.transactionCount} label="Transactions" />
        <StatBlock value={formatAmount(activity.totalVolume.amount, activity.totalVolume.currency)} label="Total volume" />
        <StatBlock value={activity.unsuccessfulCount} label="Unsuccessful" />
      </div>

      {activeChannels.length === 0 ? (
        <StatusPanel>No activity in this window.</StatusPanel>
      ) : (
        activeChannels.map(({ channel, summary }) => (
          <ChannelSection key={channel} channel={channel} summary={summary} transactions={activity.recentTransactions} />
        ))
      )}
    </div>
  );
}

function FindingCard({ finding }: { finding: RiskFinding }) {
  return (
    <div className="flex flex-col gap-2 border-t border-rule py-6 first:border-t-0 first:pt-0">
      <div className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1">
        <div className="flex items-baseline gap-3">
          <span className="font-mono text-sm text-ink-faint">{finding.ruleCode}</span>
          <h4 className="text-base font-bold text-ink">{finding.ruleName}</h4>
        </div>
        <span className="font-mono text-sm tabular-nums text-ink-muted">+{finding.contribution.toFixed(2)}</span>
      </div>
      <p className="max-w-2xl text-sm text-ink-muted">{finding.condition}</p>
      <p className="font-mono text-xs tracking-wide text-ink-faint uppercase">
        {finding.channel} · {formatDate(finding.occurredAt)} · {truncateMiddle(finding.transactionId, 8, 4)}
      </p>
    </div>
  );
}

function RiskContent({ risk }: { risk: CustomerRiskReport }) {
  return (
    <div className="flex flex-col gap-10">
      <p className="text-sm text-ink-faint">
        {formatDate(risk.from)} – {formatDate(risk.to)}
      </p>
      <div className="flex flex-wrap items-end gap-10">
        <StatBlock value={risk.score.toFixed(2)} label="Score" tone={risk.level === 'CRITICAL' ? 'accent' : 'default'} />
        <div className="flex flex-col gap-2">
          <span className={labelClasses}>Level</span>
          <RiskBadge level={risk.level} />
        </div>
        <StatBlock value={risk.transactionsEvaluated} label="Transactions evaluated" />
      </div>

      {risk.findings.length === 0 ? (
        <StatusPanel>No rules fired in this window.</StatusPanel>
      ) : (
        <div className="flex flex-col">
          {risk.findings.map((finding, index) => (
            <FindingCard key={`${finding.transactionId}-${finding.ruleCode}-${index}`} finding={finding} />
          ))}
        </div>
      )}
    </div>
  );
}

function ActivitySection({ state }: { state: AsyncState<ActivityOverview> }) {
  if (state.status === 'loading') return <StatusPanel>Loading activity…</StatusPanel>;
  if (state.status === 'error') return <ErrorPanel message={state.message} />;
  return <ActivityContent activity={state.data} />;
}

function RiskSection({ state }: { state: AsyncState<CustomerRiskReport> }) {
  if (state.status === 'loading') return <StatusPanel>Evaluating risk…</StatusPanel>;
  if (state.status === 'error') return <ErrorPanel message={state.message} />;
  return <RiskContent risk={state.data} />;
}

/**
 * The customer detail screen: identity, summary statistics, activity split
 * across CARD / PAYMENT / CRYPTO with each channel's own table, and the risk
 * score with the rules that fired.
 *
 * Three independent fetches, not one: identity resolves first (it accepts
 * either a UUID or a printed reference), then activity and risk — both of
 * which need the customer's UUID — run once it has. Each gets its own
 * loading/error state (`useAsync`), so a slow risk evaluation never blocks
 * the activity table from appearing, and a failure in one never blanks the
 * other two.
 */
export function CustomerDetailPage() {
  const { idOrReference = '' } = useParams<{ idOrReference: string }>();

  const customerState = useAsync(() => api.getCustomer(idOrReference), [idOrReference]);
  const customerId = customerState.status === 'ready' ? customerState.data.customerId : null;

  const activityState = useAsync(customerId ? () => api.getCustomerActivity(customerId) : null, [customerId]);
  const riskState = useAsync(customerId ? () => api.getCustomerRisk(customerId) : null, [customerId]);

  return (
    <PageShell>
      <div className="flex flex-col gap-12">
        <Link to="/customers" className="text-sm font-medium text-ink-muted hover:text-ink hover:underline">
          ← Back to search
        </Link>

        {customerState.status === 'loading' ? <StatusPanel>Loading customer…</StatusPanel> : null}
        {customerState.status === 'error' ? <ErrorPanel message={customerState.message} /> : null}

        {customerState.status === 'ready' ? (
          <>
            <CustomerIdentity customer={customerState.data} />

            <section className="flex flex-col gap-8 border-t border-rule pt-10">
              <div className="flex flex-col gap-2">
                <p className={labelClasses}>Activity</p>
                <h2 className="text-xl font-bold">Card, payment and crypto</h2>
              </div>
              <ActivitySection state={activityState} />
            </section>

            <section className="flex flex-col gap-8 border-t border-rule pt-10">
              <div className="flex flex-col gap-2">
                <p className={labelClasses}>Risk</p>
                <h2 className="text-xl font-bold">Rules evaluation</h2>
              </div>
              <RiskSection state={riskState} />
            </section>

            <section className="flex flex-col gap-8 border-t border-rule pt-10">
              <div className="flex flex-col gap-2">
                <p className={labelClasses}>Analysis</p>
                <h2 className="text-xl font-bold">AI risk analysis</h2>
              </div>
              {customerId ? <AnalysisSection customerId={customerId} /> : null}
            </section>
          </>
        ) : null}
      </div>
    </PageShell>
  );
}
