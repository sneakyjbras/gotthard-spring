import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';
import { labelClasses } from './Label';

export interface StatBlockProps {
  value: ReactNode;
  label: string;
  tone?: 'default' | 'accent';
  caption?: string;
  /** The value is numeric-tabular by default — the console's KPIs are counts and scores. */
  monospace?: boolean;
  className?: string;
}

/**
 * Large numeral as hero, small uppercase wide-tracked label beneath it —
 * the hierarchy every KPI in the console is built from.
 */
export function StatBlock({
  value,
  label,
  tone = 'default',
  caption,
  monospace = true,
  className,
}: StatBlockProps) {
  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <span
        className={cn(
          'text-3xl leading-none font-black tracking-tight tabular-nums',
          monospace && 'font-mono',
          tone === 'accent' ? 'text-accent-fg' : 'text-ink',
        )}
      >
        {value}
      </span>
      <span className={labelClasses}>{label}</span>
      {caption ? <span className="text-sm text-ink-faint">{caption}</span> : null}
    </div>
  );
}
