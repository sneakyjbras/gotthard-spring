import { useEffect, useState, type ReactNode } from 'react';
import { Button, labelClasses, RiskBadge } from '@/components/primitives';
import { api } from '@/lib/api/client';
import { ApiError } from '@/lib/api/contract';
import type { AiAnalysis } from '@/lib/api/types';
import { useAsync, type AsyncState } from '@/lib/hooks/useAsync';
import { cn } from '@/lib/utils/cn';
import { formatDateTime } from '@/lib/utils/format';

export interface AnalysisSectionProps {
  customerId: string;
}

type RunState = { status: 'idle' } | { status: 'pending' } | { status: 'error'; message: string };

const runErrorMessage = (error: unknown): string =>
  error instanceof ApiError ? error.message : 'Something went wrong running the analysis. Try again.';

function StatusPanel({ children }: { children: ReactNode }) {
  return (
    <div className="border-t border-rule py-10 text-center">
      <p className="text-sm text-ink-faint">{children}</p>
    </div>
  );
}

function ErrorPanel({ message }: { message: string }) {
  return (
    <div className="border-t border-rule py-6">
      <p role="alert" className="text-sm text-accent-fg">
        {message}
      </p>
    </div>
  );
}

/**
 * Conveys "the model is thinking", not "a table is loading" — three staggered pulses rather than the
 * plain text `StatusPanel` uses elsewhere, because this wait is a different kind: seconds, not
 * milliseconds, spent on an LLM call rather than a database round trip.
 */
function ThinkingIndicator() {
  return (
    <div className="flex items-center gap-3 border-t border-rule py-6" role="status" aria-live="polite">
      <span className="flex items-center gap-1.5" aria-hidden="true">
        <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-ink [animation-delay:0ms]" />
        <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-ink [animation-delay:200ms]" />
        <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-ink [animation-delay:400ms]" />
      </span>
      <p className="text-sm text-ink-muted">The model is reviewing this customer&rsquo;s activity — this can take several seconds.</p>
    </div>
  );
}

/**
 * Provider and model, always shown next to a verdict. Deliberately plain — a bordered tag in the same
 * grayscale vocabulary as everything that is not a risk signal, so it reads as provenance metadata,
 * not as an alert. A reviewer with no `ANTHROPIC_API_KEY` sees `stub · stub-analyst-v1` here and the
 * caption beneath it, which is what stands between that reviewer and mistaking a stub for a verdict.
 */
function ProvenanceTag({ provider, model }: { provider: string; model: string }) {
  const isStub = provider === 'stub';
  return (
    <div className="flex flex-col gap-1.5">
      <span className="inline-flex w-fit items-center gap-2 border border-rule px-2.5 py-1 font-mono text-xs tracking-wider text-ink-muted uppercase">
        {provider} · {model}
      </span>
      {isStub ? (
        <p className="text-xs text-ink-faint">No API key configured — this is the stub adapter&rsquo;s output, not a model&rsquo;s.</p>
      ) : null}
    </div>
  );
}

/**
 * The rules' level and the model's own, side by side. When they differ, `levelsDiverged` — computed by
 * PostgreSQL, not by this component — says so in plain grayscale text beneath the two badges: an audit
 * signal to notice, not a warning to react to, so it borrows none of `RiskBadge`'s accent.
 */
function LevelComparison({ analysis }: { analysis: AiAnalysis }) {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-end gap-10">
        <div className="flex flex-col gap-2">
          <span className={labelClasses}>Rules computed</span>
          <RiskBadge level={analysis.computedLevel} />
        </div>
        <div className="flex flex-col gap-2">
          <span className={labelClasses}>Model assessed</span>
          <RiskBadge level={analysis.assessedLevel} />
        </div>
      </div>
      {analysis.levelsDiverged ? (
        <p className="text-sm text-ink-muted">
          The model disagreed: it read this activity as <span className="font-medium text-ink">{analysis.assessedLevel}</span>{' '}
          against the rules&rsquo; computed <span className="font-medium text-ink">{analysis.computedLevel}</span>. See the summary
          below for why.
        </p>
      ) : null}
    </div>
  );
}

function RecommendationList({ recommendations }: { recommendations: readonly string[] }) {
  return (
    <ul className="flex list-outside list-disc flex-col gap-2 pl-5 text-sm text-ink">
      {recommendations.map((recommendation) => (
        <li key={recommendation}>{recommendation}</li>
      ))}
    </ul>
  );
}

function CitationCard({ citation }: { citation: AiAnalysis['citations'][number] }) {
  return (
    <div className="flex flex-col gap-1.5 border-t border-rule py-5 first:border-t-0 first:pt-0">
      <div className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1">
        <div className="flex items-baseline gap-3">
          <span className="font-mono text-sm text-ink-faint">
            {citation.document} §{citation.section}
          </span>
          <h5 className="text-sm font-bold text-ink">{citation.title}</h5>
        </div>
        <span className="font-mono text-xs tabular-nums text-ink-faint">sim {citation.similarity.toFixed(2)}</span>
      </div>
      <p className="max-w-2xl text-sm text-ink-muted">{citation.body}</p>
    </div>
  );
}

function AnalysisDetail({ analysis }: { analysis: AiAnalysis }) {
  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-start justify-between gap-6">
        <div className="flex flex-col gap-1">
          <span className="font-mono text-sm text-ink-muted">{formatDateTime(analysis.createdAt)}</span>
          <span className="text-sm text-ink-muted">Requested by {analysis.requestedBy.displayName}</span>
        </div>
        <ProvenanceTag provider={analysis.provider} model={analysis.model} />
      </div>

      <LevelComparison analysis={analysis} />

      <div className="flex flex-col gap-2">
        <span className={labelClasses}>Summary</span>
        <p className="max-w-3xl text-base text-ink">{analysis.summary}</p>
      </div>

      <div className="flex flex-col gap-3">
        <span className={labelClasses}>Recommendations</span>
        <RecommendationList recommendations={analysis.recommendations} />
      </div>

      <div className="flex flex-col gap-2">
        <span className={labelClasses}>Policy cited</span>
        {analysis.citations.length === 0 ? (
          <p className="text-sm text-ink-faint">No policy was retrieved — no rule fired in this window.</p>
        ) : (
          <div className="flex flex-col">
            {analysis.citations.map((citation) => (
              <CitationCard key={citation.chunkId} citation={citation} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function SelectedAnalysisPanel({ state }: { state: AsyncState<AiAnalysis> | null }) {
  if (state === null) {
    return <StatusPanel>No analysis has been run for this customer yet. Run one to see the model&rsquo;s read.</StatusPanel>;
  }
  if (state.status === 'loading') return <StatusPanel>Loading analysis…</StatusPanel>;
  if (state.status === 'error') return <ErrorPanel message={state.message} />;
  return <AnalysisDetail analysis={state.data} />;
}

function HistoryRow({
  analysis,
  isSelected,
  onSelect,
}: {
  analysis: AiAnalysis;
  isSelected: boolean;
  onSelect: () => void;
}) {
  return (
    <li>
      <button
        type="button"
        onClick={onSelect}
        aria-pressed={isSelected}
        className={cn(
          'flex w-full flex-wrap items-center justify-between gap-x-6 gap-y-2 border-t border-rule py-4 text-left first:border-t-0',
          'transition-colors duration-100 hover:bg-paper-subtle',
          isSelected && 'bg-paper-subtle',
        )}
      >
        <div className="flex flex-col gap-1">
          <span className="font-mono text-sm text-ink">{formatDateTime(analysis.createdAt)}</span>
          <span className="text-sm text-ink-muted">
            {analysis.requestedBy.displayName} <span className="text-ink-faint">· {analysis.provider}</span>
          </span>
        </div>
        <div className="flex items-center gap-3">
          {analysis.levelsDiverged ? <span className="text-xs tracking-wide text-ink-faint uppercase">model disagreed</span> : null}
          <RiskBadge level={analysis.computedLevel} />
        </div>
      </button>
    </li>
  );
}

function HistoryList({
  history,
  selectedId,
  onSelect,
}: {
  history: readonly AiAnalysis[];
  selectedId: string | null;
  onSelect: (analysisId: string) => void;
}) {
  if (history.length === 0) {
    return <StatusPanel>No past analyses. Every analysis run for this customer will be listed here, newest first.</StatusPanel>;
  }
  return (
    <ul className="flex flex-col">
      {history.map((analysis) => (
        <HistoryRow
          key={analysis.analysisId}
          analysis={analysis}
          isSelected={analysis.analysisId === selectedId}
          onSelect={() => onSelect(analysis.analysisId)}
        />
      ))}
    </ul>
  );
}

/**
 * Merges analyses run this session ahead of whatever `getAnalysisHistory` last returned, de-duplicated
 * by id. The freshly run one is always newer than anything already fetched, so it is safe to put it
 * first rather than re-sorting by timestamp.
 */
function mergeHistory(fetched: readonly AiAnalysis[], runThisSession: readonly AiAnalysis[]): AiAnalysis[] {
  const fetchedIds = new Set(fetched.map((analysis) => analysis.analysisId));
  return [...runThisSession.filter((analysis) => !fetchedIds.has(analysis.analysisId)), ...fetched];
}

/**
 * The AI analysis panel and its history, together: spec item 5's visible half. Running an analysis
 * and reviewing one already run are the same detail view — `AnalysisDetail` — reached two ways: a
 * fresh run selects itself automatically, and a history row selects on click.
 *
 * Three pieces of state, kept apart on purpose:
 * - `runState` — the in-flight "run a new one" action, which `useAsync` does not model (it fires on a
 *   click, not on mount or a dependency change).
 * - `historyState` — `GET .../analyses`, fetched once per customer via `useAsync` exactly like the
 *   page's own activity and risk sections.
 * - `runThisSession` — analyses `POST .../analysis` has returned this page-view, held locally so a
 *   fresh run appears in the list immediately rather than waiting on a second fetch.
 */
export function AnalysisSection({ customerId }: AnalysisSectionProps) {
  const historyState = useAsync(() => api.getAnalysisHistory(customerId), [customerId]);
  const [runState, setRunState] = useState<RunState>({ status: 'idle' });
  const [runThisSession, setRunThisSession] = useState<AiAnalysis[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detailCache, setDetailCache] = useState<Record<string, AiAnalysis>>({});

  const history = historyState.status === 'ready' ? mergeHistory(historyState.data, runThisSession) : runThisSession;

  // Nothing selected yet: default to the newest entry once one exists, rather than an empty panel.
  useEffect(() => {
    if (selectedId === null && history.length > 0) setSelectedId(history[0].analysisId);
    // oxlint-disable-next-line react-hooks/exhaustive-deps
  }, [history, selectedId]);

  const cached = selectedId ? detailCache[selectedId] : undefined;
  const selectedState = useAsync(
    selectedId ? () => (cached ? Promise.resolve(cached) : api.getAnalysis(selectedId)) : null,
    [selectedId, cached],
  );

  useEffect(() => {
    if (selectedState.status !== 'ready') return;
    const analysisId = selectedState.data.analysisId;
    setDetailCache((cache) => (cache[analysisId] ? cache : { ...cache, [analysisId]: selectedState.data }));
    // oxlint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedState]);

  async function handleRunAnalysis() {
    setRunState({ status: 'pending' });
    try {
      const analysis = await api.runAnalysis(customerId);
      setDetailCache((cache) => ({ ...cache, [analysis.analysisId]: analysis }));
      setRunThisSession((current) => [analysis, ...current]);
      setSelectedId(analysis.analysisId);
      setRunState({ status: 'idle' });
    } catch (error) {
      setRunState({ status: 'error', message: runErrorMessage(error) });
    }
  }

  return (
    <div className="flex flex-col gap-10">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <p className="max-w-2xl text-sm text-ink-muted">
          Asks the model to read the rules&rsquo; findings and the retrieved policy, and to give its own
          independent verdict alongside them.
        </p>
        <Button onClick={handleRunAnalysis} disabled={runState.status === 'pending'}>
          {runState.status === 'pending' ? 'Analysing…' : 'Run analysis'}
        </Button>
      </div>

      {runState.status === 'pending' ? <ThinkingIndicator /> : null}
      {runState.status === 'error' ? <ErrorPanel message={runState.message} /> : null}

      <SelectedAnalysisPanel state={selectedId ? selectedState : null} />

      <div className="flex flex-col gap-6 border-t border-rule pt-10">
        <div className="flex flex-col gap-2">
          <span className={labelClasses}>History</span>
          <h3 className="text-lg font-bold">Past analyses</h3>
        </div>
        {historyState.status === 'loading' ? <StatusPanel>Loading history…</StatusPanel> : null}
        {historyState.status === 'error' ? <ErrorPanel message={historyState.message} /> : null}
        {historyState.status === 'ready' || runThisSession.length > 0 ? (
          <HistoryList history={history} selectedId={selectedId} onSelect={setSelectedId} />
        ) : null}
      </div>
    </div>
  );
}
