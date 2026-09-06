import { useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { PageShell } from '@/components/layout';
import { Button, DataTable, RiskBadge, StatBlock, TextInput, type DataTableColumn } from '@/components/primitives';
import { api } from '@/lib/api/client';
import { ApiError } from '@/lib/api/contract';
import type { CustomerSummary } from '@/lib/api/types';
import { formatDate } from '@/lib/utils/format';

function RiskCell({ level }: { level: CustomerSummary['latestRiskLevel'] }) {
  if (level === null) {
    return <span className="text-ink-faint">Not assessed</span>;
  }
  return <RiskBadge level={level} />;
}

/** The one way a search result opens the customer detail screen. Reference and name both link — the bigger of two small targets, not a whole clickable row (`DataTable` renders each cell's own content; see the primitive). */
function CustomerLink({ customer, children }: { customer: CustomerSummary; children: ReactNode }) {
  return (
    <Link to={`/customers/${encodeURIComponent(customer.reference)}`} className="hover:underline focus-visible:underline">
      {children}
    </Link>
  );
}

export function CustomerSearchPage() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<CustomerSummary[]>([]);
  const [hasSearched, setHasSearched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const columns = useMemo<DataTableColumn<CustomerSummary>[]>(
    () => [
      {
        key: 'reference',
        header: 'Reference',
        monospace: true,
        render: (c) => <CustomerLink customer={c}>{c.reference}</CustomerLink>,
      },
      { key: 'name', header: 'Name', render: (c) => <CustomerLink customer={c}>{c.fullName}</CustomerLink> },
      { key: 'segment', header: 'Segment', render: (c) => c.segment.replace('_', ' ') },
      { key: 'country', header: 'Country', render: (c) => c.country },
      { key: 'onboarded', header: 'Onboarded', render: (c) => formatDate(c.onboardedAt) },
      { key: 'risk', header: 'Risk', render: (c) => <RiskCell level={c.latestRiskLevel} /> },
    ],
    [],
  );

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = query.trim();
    if (trimmed.length === 0) return;

    setLoading(true);
    setError(null);
    try {
      const found = await api.searchCustomers(trimmed);
      setResults(found);
      setHasSearched(true);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Search failed. Try again.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <PageShell>
      <div className="flex flex-col gap-12">
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-bold">Customer search</h1>
          <p className="max-w-2xl text-sm text-ink-muted">
            Look up a customer by their exact reference or customer ID to review their card, payment
            and cryptocurrency activity.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4 sm:flex-row sm:items-end sm:gap-6">
          <div className="flex-1 sm:max-w-md">
            <TextInput
              label="Search"
              placeholder="CH-4410-8821, Elena Fischer…"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              monospace
            />
          </div>
          <Button type="submit" disabled={loading}>
            {loading ? 'Searching…' : 'Search'}
          </Button>
        </form>

        {error ? <p className="text-sm text-accent-fg">{error}</p> : null}

        {!hasSearched ? (
          <div className="border-t border-rule py-16 text-center">
            <p className="text-sm text-ink-faint">Search for a customer by reference, name or ID.</p>
          </div>
        ) : (
          <div className="flex flex-col gap-8">
            <StatBlock value={results.length} label="Results found" />
            <DataTable
              columns={columns}
              rows={results}
              getRowKey={(customer) => customer.customerId}
              emptyMessage="No customer matches that search."
            />
          </div>
        )}
      </div>
    </PageShell>
  );
}
