import { useId, type InputHTMLAttributes } from 'react';
import { cn } from '@/lib/utils/cn';
import { labelClasses } from './Label';

export interface TextInputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: string;
  /** Renders the value in the tabular monospace face — for references, IBANs, ids. */
  monospace?: boolean;
}

/**
 * An underline field, not a boxed one: a single hairline rule carries the
 * whole affordance, thickening to ink on focus. No border box, no shadow —
 * the same restraint the rest of the system uses for separation.
 */
export function TextInput({
  label,
  error,
  hint,
  monospace = false,
  className,
  id,
  required,
  ...props
}: TextInputProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const describedBy = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined;

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={inputId} className={labelClasses}>
        {label}
        {required ? <span className="text-accent-fg"> *</span> : null}
      </label>
      <input
        id={inputId}
        required={required}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={cn(
          'h-11 border-0 border-b border-rule bg-transparent text-base text-ink',
          'placeholder:text-ink-faint',
          'outline-none transition-colors duration-100 focus:border-b-2 focus:border-ink focus:pb-px',
          error && 'border-accent-fg focus:border-accent-fg',
          monospace && 'font-mono tabular-nums',
          className,
        )}
        {...props}
      />
      {error ? (
        <p id={`${inputId}-error`} className="text-sm text-accent-fg">
          {error}
        </p>
      ) : hint ? (
        <p id={`${inputId}-hint`} className="text-sm text-ink-faint">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
