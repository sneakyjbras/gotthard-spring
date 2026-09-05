import { Link } from 'react-router-dom';
import { PageShell } from '@/components/layout';
import { StatBlock } from '@/components/primitives';

export function NotFoundPage() {
  return (
    <PageShell>
      <div className="flex flex-col items-start gap-6 py-24">
        <StatBlock value="404" label="Page not found" />
        <p className="max-w-sm text-sm text-ink-muted">
          The page you are looking for does not exist, or has not been built yet.
        </p>
        <Link to="/" className="text-sm font-medium text-ink underline underline-offset-4">
          Back to Gotthard Spring
        </Link>
      </div>
    </PageShell>
  );
}
