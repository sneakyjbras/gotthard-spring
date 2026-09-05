import type { ReactNode } from 'react';
import { Header } from './Header';

export interface PageShellProps {
  children: ReactNode;
}

/**
 * The chrome every authenticated route (and the styleguide) renders inside:
 * header, then content constrained to the strict grid's outer bound, then a
 * closing footer rule. Pages provide only what sits between the two rules.
 */
export function PageShell({ children }: PageShellProps) {
  return (
    <div className="flex min-h-svh flex-col bg-paper text-ink">
      <Header />
      <main className="mx-auto w-full max-w-[var(--container-max)] flex-1 px-6 py-12 sm:px-10">{children}</main>
      <footer className="border-t border-rule">
        <div className="mx-auto max-w-[var(--container-max)] px-6 py-6 sm:px-10">
          <p className="text-xs tracking-wide text-ink-faint uppercase">Gotthard Spring — operator console</p>
        </div>
      </footer>
    </div>
  );
}
