type ClassValue = string | number | null | undefined | false | Record<string, boolean | undefined>;

/** Joins conditional class names. No dependency — the logic is a few lines. */
export function cn(...values: ClassValue[]): string {
  return values
    .flatMap((value) => {
      if (!value) return [];
      if (typeof value === 'string' || typeof value === 'number') return [String(value)];
      return Object.entries(value)
        .filter(([, enabled]) => enabled)
        .map(([key]) => key);
    })
    .join(' ');
}
