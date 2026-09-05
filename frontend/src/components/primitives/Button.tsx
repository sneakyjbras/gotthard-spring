import type { ButtonHTMLAttributes } from 'react';
import { cn } from '@/lib/utils/cn';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'md' | 'sm';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
}

const base = [
  'inline-flex items-center justify-center gap-2 border font-medium uppercase tracking-wide',
  'transition-colors duration-100',
  'disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-40',
].join(' ');

// `danger` is the one variant allowed to reach for the accent colour outside
// RiskBadge — a destructive action is itself a risk the UI should mark.
const variantClasses: Record<ButtonVariant, string> = {
  primary: 'border-ink bg-ink text-paper hover:bg-paper hover:text-ink',
  secondary: 'border-ink bg-transparent text-ink hover:bg-ink hover:text-paper',
  ghost: 'border-transparent bg-transparent text-ink hover:border-rule',
  danger: 'border-accent-fg bg-transparent text-accent-fg hover:bg-accent-fg hover:text-accent-on',
};

const sizeClasses: Record<ButtonSize, string> = {
  md: 'h-11 px-6 text-sm',
  sm: 'h-8 px-4 text-xs',
};

export function Button({ variant = 'primary', size = 'md', className, ...props }: ButtonProps) {
  return (
    <button
      type="button"
      className={cn(base, variantClasses[variant], sizeClasses[size], className)}
      {...props}
    />
  );
}
