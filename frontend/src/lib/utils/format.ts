/**
 * Formatting helpers for the tabular-monospace data surfaces: dates,
 * amounts, IBANs, PANs and hashes. Every function here is pure — components
 * decide the font, this module only decides the text.
 */

// en-GB reads day-month-year unambiguously regardless of the visitor's locale.
const dateFormatter = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });

// de-CH gives the Swiss apostrophe thousands separator (e.g. "CHF 1'234.50"),
// the convention this bank's own statements would use, independent of currency.
const amountFormatterCache = new Map<string, Intl.NumberFormat>();

export function formatDate(iso: string): string {
  return dateFormatter.format(new Date(iso));
}

export function formatAmount(amount: number, currency: string): string {
  let formatter = amountFormatterCache.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat('de-CH', { style: 'currency', currency, currencyDisplay: 'code' });
    amountFormatterCache.set(currency, formatter);
  }
  return formatter.format(amount);
}

/** Groups a raw IBAN/account string into 4-character blocks for display. */
export function formatIban(raw: string): string {
  return (raw.match(/.{1,4}/g) ?? [raw]).join(' ');
}

/** Masks a card PAN to the last 4 digits, grouped like a printed card. */
export function maskPan(pan: string): string {
  const digits = pan.replace(/\s+/g, '');
  const last4 = digits.slice(-4);
  return `•••• •••• •••• ${last4}`;
}

/** Collapses a long hash/wallet address to its identifying ends: `4a5e1e…deda33`. */
export function truncateMiddle(value: string, headChars = 6, tailChars = 6): string {
  if (value.length <= headChars + tailChars + 1) return value;
  // `.slice(-0)` returns the *whole* string, not "the last zero characters" —
  // guard tailChars === 0 explicitly rather than relying on negative-index slicing.
  const tail = tailChars > 0 ? value.slice(-tailChars) : '';
  return `${value.slice(0, headChars)}…${tail}`;
}
