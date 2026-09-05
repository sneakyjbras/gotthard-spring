import type { ComponentPropsWithoutRef } from 'react';
import { cn } from '@/lib/utils/cn';

/**
 * Shared styling for "the small uppercase label with wide letter-spacing" —
 * used beneath stat numerals, above form fields, and inside table headers.
 * Exported as a class string (not a polymorphic component) so a real
 * `<label htmlFor>` can carry the exact same look as a plain `<span>`.
 */
export const labelClasses = 'text-sm font-medium uppercase tracking-widest text-ink-muted';

export function Label({ className, ...props }: ComponentPropsWithoutRef<'span'>) {
  return <span className={cn(labelClasses, className)} {...props} />;
}
