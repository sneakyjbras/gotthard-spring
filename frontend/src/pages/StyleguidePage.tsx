import { useState, type ReactNode } from 'react';
import { PageShell } from '@/components/layout';
import {
  Button,
  DataTable,
  labelClasses,
  RiskBadge,
  StatBlock,
  TextInput,
  type DataTableColumn,
} from '@/components/primitives';
import { mockTransactions } from '@/lib/api/mock-data';
import { RISK_LEVELS, type Transaction } from '@/lib/api/types';
import { cn } from '@/lib/utils/cn';
import { formatAmount, formatDate, formatIban, maskPan, truncateMiddle } from '@/lib/utils/format';

const TYPE_SCALE = [
  { name: 'xs', px: '11px', className: 'text-xs' },
  { name: 'sm', px: '13px', className: 'text-sm' },
  { name: 'base', px: '15px', className: 'text-base' },
  { name: 'lg', px: '19px', className: 'text-lg' },
  { name: 'xl', px: '24px', className: 'text-xl' },
  { name: '2xl', px: '32px', className: 'text-2xl' },
  { name: '3xl', px: '48px', className: 'text-3xl' },
  { name: '4xl', px: '72px', className: 'text-4xl' },
] as const;

const COLOR_TOKENS = [
  { token: '--color-paper', className: 'bg-paper', usage: 'Page background' },
  { token: '--color-paper-subtle', className: 'bg-paper-subtle', usage: 'Zebra rows, hover fill' },
  { token: '--color-ink', className: 'bg-ink', usage: 'Primary text' },
  { token: '--color-ink-muted', className: 'bg-ink-muted', usage: 'Secondary text, table headers' },
  { token: '--color-ink-faint', className: 'bg-ink-faint', usage: 'Placeholder / disabled only' },
  { token: '--color-rule', className: 'bg-rule', usage: 'Hairline separators' },
  { token: '--color-rule-strong', className: 'bg-rule-strong', usage: 'Rare emphasis rule' },
] as const;

function Section({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow: string;
  title: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <section className="flex flex-col gap-6 border-t border-rule pt-10">
      <div className="flex flex-col gap-2">
        <p className={labelClasses}>{eyebrow}</p>
        <h2 className="text-xl font-bold">{title}</h2>
        {description ? <p className="max-w-2xl text-sm text-ink-muted">{description}</p> : null}
      </div>
      {children}
    </section>
  );
}

function ColorSwatch({ token, className, usage }: { token: string; className: string; usage: string }) {
  return (
    <div className="flex flex-col gap-3">
      <div className={cn('h-16 w-full border border-rule', className)} />
      <div>
        <p className="font-mono text-sm text-ink">{token}</p>
        <p className="text-sm text-ink-faint">{usage}</p>
      </div>
    </div>
  );
}

function GridDemo() {
  const [showGuides, setShowGuides] = useState(true);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-ink-muted">12 columns, one consistent gutter — every layout in this console aligns to it.</p>
        <Button variant="ghost" size="sm" onClick={() => setShowGuides((current) => !current)}>
          {showGuides ? 'Hide guides' : 'Show guides'}
        </Button>
      </div>
      <div className="relative border border-rule p-4">
        <div className="grid grid-cols-12 gap-4">
          {Array.from({ length: 12 }, (_, index) => (
            <div key={index} className="h-20 bg-paper-subtle" />
          ))}
        </div>
        {showGuides ? (
          <div className="pointer-events-none absolute inset-4 grid grid-cols-12 gap-4">
            {Array.from({ length: 12 }, (_, index) => (
              <div key={index} className="h-full border-x border-ink/15" />
            ))}
          </div>
        ) : null}
      </div>
    </div>
  );
}

function describeCounterparty(transaction: Transaction): string {
  switch (transaction.activityType) {
    case 'CARD':
      return transaction.merchantName;
    case 'PAYMENT':
      return `Wire → ${transaction.receiverBankCountry}`;
    case 'CRYPTO':
      return transaction.exchangeName ?? transaction.blockchain;
  }
}

function IdentifierCell({ transaction }: { transaction: Transaction }) {
  const identifier =
    transaction.activityType === 'CARD'
      ? maskPan(transaction.cardPan)
      : transaction.activityType === 'PAYMENT'
        ? formatIban(transaction.receiverAccount)
        : truncateMiddle(transaction.txHash);
  return <span className="font-mono tabular-nums">{identifier}</span>;
}

const transactionColumns: DataTableColumn<Transaction>[] = [
  {
    key: 'id',
    header: 'Transaction',
    monospace: true,
    render: (transaction) => truncateMiddle(transaction.transactionId, 8, 4),
  },
  { key: 'type', header: 'Type', render: (transaction) => transaction.activityType },
  { key: 'counterparty', header: 'Counterparty', render: (transaction) => describeCounterparty(transaction) },
  {
    key: 'identifier',
    header: 'PAN / IBAN / Hash',
    render: (transaction) => <IdentifierCell transaction={transaction} />,
  },
  {
    key: 'amount',
    header: 'Amount',
    numeric: true,
    render: (transaction) => formatAmount(transaction.amount, transaction.currency),
  },
  { key: 'status', header: 'Status', render: (transaction) => transaction.status },
  { key: 'date', header: 'Date', monospace: true, render: (transaction) => formatDate(transaction.createdAt) },
];

export function StyleguidePage() {
  return (
    <PageShell>
      <div className="flex flex-col gap-16">
        <div className="flex flex-col gap-2">
          <p className={labelClasses}>Design system</p>
          <h1 className="text-2xl font-bold">Styleguide</h1>
          <p className="max-w-2xl text-sm text-ink-muted">
            Every token and primitive Gotthard Spring is built from. The page&rsquo;s own header, above,
            is the page-shell primitive — nothing below re-renders it.
          </p>
        </div>

        <Section
          eyebrow="Foundations"
          title="Colour"
          description="Black, white and grey carry the interface. Tokens are CSS custom properties, redefined for dark — inspect any of these in devtools."
        >
          <div className="grid grid-cols-2 gap-8 sm:grid-cols-4">
            {COLOR_TOKENS.map((swatch) => (
              <ColorSwatch key={swatch.token} {...swatch} />
            ))}
          </div>
        </Section>

        <Section
          eyebrow="Foundations"
          title="Type scale"
          description="Deliberate jumps, not a smooth ramp. Archivo for reading, IBM Plex Mono for anything that has to align."
        >
          <div className="flex flex-col gap-6">
            {TYPE_SCALE.map((step) => (
              <div key={step.name} className="flex items-baseline gap-6 border-b border-rule pb-6">
                <span className="w-24 shrink-0 font-mono text-sm text-ink-faint">
                  {step.name} · {step.px}
                </span>
                <span className={cn('font-bold tracking-tight text-ink', step.className)}>Gotthard Spring</span>
              </div>
            ))}
          </div>
        </Section>

        <Section eyebrow="Foundations" title="Grid" description="The strict grid the rest of this page — and every other page — aligns to.">
          <GridDemo />
        </Section>

        <Section eyebrow="Components" title="Button" description="Sharp corners, no shadow. Hover inverts fill instead of lifting.">
          <div className="flex flex-col gap-6">
            <div className="flex flex-wrap items-center gap-4">
              <Button variant="primary">Primary</Button>
              <Button variant="secondary">Secondary</Button>
              <Button variant="ghost">Ghost</Button>
              <Button variant="danger">Danger</Button>
              <Button variant="primary" disabled>
                Disabled
              </Button>
            </div>
            <div className="flex flex-wrap items-center gap-4">
              <Button size="sm" variant="secondary">
                Small
              </Button>
              <Button size="md" variant="secondary">
                Medium
              </Button>
            </div>
          </div>
        </Section>

        <Section eyebrow="Components" title="Text input" description="An underline field, not a box. The rule thickens to ink on focus.">
          <div className="grid max-w-2xl gap-8 sm:grid-cols-2">
            <TextInput label="Customer reference" placeholder="CH-4410-8821" monospace />
            <TextInput label="Full name" defaultValue="Elena Fischer" />
            <TextInput label="Amount" defaultValue="12'400.00" monospace error="Exceeds daily transfer limit." />
            <TextInput label="Notes" placeholder="Optional" disabled />
          </div>
        </Section>

        <Section
          eyebrow="Components"
          title="Risk badge"
          description="The one place the accent colour appears outside a destructive action. LOW and MEDIUM stay grayscale; HIGH steps up to an accent outline; only CRITICAL is filled."
        >
          <div className="flex flex-wrap items-center gap-4">
            {RISK_LEVELS.map((level) => (
              <RiskBadge key={level} level={level} />
            ))}
          </div>
        </Section>

        <Section eyebrow="Components" title="Stat / KPI block" description="Large numeral as hero, small uppercase wide-tracked label beneath it.">
          <div className="grid grid-cols-2 gap-8 sm:grid-cols-4">
            <StatBlock value="128" label="Customers" />
            <StatBlock value="42" label="Analyses this week" />
            <StatBlock value="3" label="Critical alerts" tone="accent" />
            <StatBlock value="96%" label="Rules coverage" monospace={false} />
          </div>
        </Section>

        <Section
          eyebrow="Components"
          title="Data table"
          description="Horizontal hairlines only. Amounts right-align in tabular monospace; ids, IBANs and hashes stay left-aligned but keep the same monospace face so columns still line up."
        >
          <DataTable
            columns={transactionColumns}
            rows={mockTransactions}
            getRowKey={(transaction) => transaction.transactionId}
          />
        </Section>
      </div>
    </PageShell>
  );
}
