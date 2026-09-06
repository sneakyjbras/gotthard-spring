import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api } from '../api/client';
import { setSessionExpiredHandler } from '../api/session-events';
import type { Operator } from '../api/types';
import { AuthContext, type AuthContextValue } from './AuthContext';

const STORAGE_KEY = 'gotthard.session';

function readStoredOperator(): Operator | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Operator) : null;
  } catch {
    // Corrupt or inaccessible storage is equivalent to "signed out", not a crash.
    return null;
  }
}

function persistOperator(operator: Operator | null): void {
  try {
    if (operator) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(operator));
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // Best-effort — a private-browsing tab that refuses storage still gets a working session.
  }
}

/**
 * Wraps the app; makes `useAuth` available to every route.
 *
 * The session itself lives on the backend, as a cookie — `operator` here is
 * a client-side mirror of it, not the source of truth. Two things keep it
 * honest:
 *
 * 1. On mount, `api.getSession()` (`GET /api/auth/me`) confirms whether the
 *    cookie is actually still valid, reconciling the optimistic value read
 *    from `localStorage` — that cache exists only so a refresh does not
 *    flash an authenticated page over to `/login` and back while the check
 *    is in flight.
 * 2. `setSessionExpiredHandler` (`../api/session-events`) subscribes to any
 *    401 the HTTP client discovers later, mid-session — an idle timeout, a
 *    backend restart — clearing `operator` immediately so `ProtectedRoute`
 *    drops back to `/login` instead of a page rendering with no data.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [operator, setOperator] = useState<Operator | null>(readStoredOperator);
  const [isInitializing, setIsInitializing] = useState(true);

  useEffect(() => {
    setSessionExpiredHandler(() => {
      persistOperator(null);
      setOperator(null);
    });
    return () => setSessionExpiredHandler(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    api
      .getSession()
      .then((current) => {
        if (cancelled) return;
        persistOperator(current);
        setOperator(current);
      })
      .catch(() => {
        // Can't reach the backend to confirm either way — trust the cached
        // operator rather than bouncing a reviewer to login over a network blip.
      })
      .finally(() => {
        if (!cancelled) setIsInitializing(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const loggedInOperator = await api.login({ username, password });
    persistOperator(loggedInOperator);
    setOperator(loggedInOperator);
  }, []);

  const logout = useCallback(() => {
    persistOperator(null);
    setOperator(null);
    void api.logout();
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ operator, isAuthenticated: operator !== null, isInitializing, login, logout }),
    [operator, isInitializing, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
