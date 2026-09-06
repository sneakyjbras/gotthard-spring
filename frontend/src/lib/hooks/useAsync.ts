import { useEffect, useRef, useState } from 'react';
import { ApiError } from '@/lib/api/contract';

export type AsyncState<T> = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; data: T };

const defaultErrorMessage = (error: unknown): string =>
  error instanceof ApiError ? error.message : 'Something went wrong. Try again.';

/**
 * Runs an async loader on mount, and again whenever `deps` changes, tracking
 * loading/error/ready as one value instead of a page juggling three booleans
 * by hand. Every fetch on the customer detail screen — identity, activity,
 * risk — goes through this, which is what makes "loading and error states on
 * every fetch" a property of the hook rather than something re-typed per
 * section.
 *
 * `loader` may be `null` to mean "not ready to fetch yet" (the customer
 * detail screen's activity and risk calls need the customer's UUID, which
 * the identity fetch resolves first) — the state stays `'loading'` and
 * nothing is called until a non-null loader is passed.
 *
 * A response that arrives after `deps` has already moved on (a slow first
 * request outlived by a fast second one) is dropped rather than applied —
 * `generation` guards that without needing an `AbortController` per caller.
 */
export function useAsync<T>(
  loader: (() => Promise<T>) | null,
  deps: readonly unknown[],
  errorMessage: (error: unknown) => string = defaultErrorMessage,
): AsyncState<T> {
  const [state, setState] = useState<AsyncState<T>>({ status: 'loading' });
  const generation = useRef(0);

  // `loader` and `errorMessage` are recreated every render by design (they close over page state);
  // re-running only on `deps` is the point of this hook, so it is the caller's own dependency list.
  // oxlint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    setState({ status: 'loading' });
    if (!loader) return;

    const thisRun = ++generation.current;
    loader()
      .then((data) => {
        if (generation.current === thisRun) setState({ status: 'ready', data });
      })
      .catch((error: unknown) => {
        if (generation.current === thisRun) setState({ status: 'error', message: errorMessage(error) });
      });
    // oxlint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return state;
}
