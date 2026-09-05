import type { RiskLevel } from '@/lib/api/types';
import { cn } from '@/lib/utils/cn';

export interface RiskBadgeProps {
  level: RiskLevel;
  className?: string;
}

/**
 * LOW and MEDIUM stay strictly grayscale — a step in border/text weight,
 * nothing more. HIGH is the first to spend the accent, as an outline.
 * Only CRITICAL is filled. One colour used this sparingly carries more
 * weight than a four-colour traffic light would, and it means the accent
 * is never ambiguous with anything else in the interface.
 */
const styles: Record<RiskLevel, string> = {
  LOW: 'border-rule text-ink-muted',
  MEDIUM: 'border-ink text-ink',
  HIGH: 'border-accent-fg text-accent-fg',
  CRITICAL: 'border-accent bg-accent text-accent-on',
};

export function RiskBadge({ level, className }: RiskBadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center border px-2.5 py-1 text-xs font-semibold uppercase tracking-wider',
        styles[level],
        className,
      )}
    >
      {level}
    </span>
  );
}
