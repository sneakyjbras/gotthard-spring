import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

export interface DataTableColumn<T> {
  key: string;
  header: string;
  /** Right-aligns the column and sets the tabular monospace face — for
   * amounts, where digits need to stack by place value. */
  numeric?: boolean;
  /** Sets the tabular monospace face without right-aligning — for ids,
   * references, IBANs, wallet addresses and hashes: still fixed-width so
   * columns of them align, but scanned left-to-right, not compared by value. */
  monospace?: boolean;
  render: (row: T) => ReactNode;
  className?: string;
}

export interface DataTableProps<T> {
  columns: readonly DataTableColumn<T>[];
  rows: readonly T[];
  getRowKey: (row: T) => string;
  emptyMessage?: string;
  className?: string;
}

/**
 * Horizontal hairlines only — no vertical rules, no cell boxes. A single
 * heavier rule marks the header baseline; every row beneath it gets the
 * same low-contrast hairline the rest of the system uses for separation.
 */
export function DataTable<T>({
  columns,
  rows,
  getRowKey,
  emptyMessage = 'No records.',
  className,
}: DataTableProps<T>) {
  return (
    <div className={cn('w-full overflow-x-auto', className)}>
      <table className="w-full min-w-max border-collapse text-left">
        <thead>
          <tr className="border-b border-ink">
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                className={cn(
                  'pr-6 pb-3 text-xs font-medium tracking-widest text-ink-muted uppercase last:pr-0',
                  column.numeric && 'text-right',
                )}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="py-10 text-center text-sm text-ink-faint">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            rows.map((row) => (
              <tr key={getRowKey(row)} className="border-b border-rule hover:bg-paper-subtle">
                {columns.map((column) => (
                  <td
                    key={column.key}
                    className={cn(
                      'py-3 pr-6 text-base text-ink last:pr-0',
                      (column.numeric || column.monospace) && 'font-mono tabular-nums',
                      column.numeric && 'text-right',
                      column.className,
                    )}
                  >
                    {column.render(row)}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
