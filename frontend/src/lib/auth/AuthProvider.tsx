import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { api } from '../api/client';
import type { Operator } from '../api/types';
import { AuthContext, type AuthContextValue } from './AuthContext';

const STORAGE_KEY = 'gotthard.session';

interface StoredSession {
  operator: Operator;
  token: string;
}

function readStoredOperator(): Operator | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    return (JSON.parse(raw) as StoredSession).operator;
  } catch {
    // Corrupt or inaccessible storage is equivalent to "signed out", not a crash.
    return null;
  }
}

function persistSession(session: StoredSession | null): void {
  try {
    if (session) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // Best-effort — a private-browsing tab that refuses storage still gets a working session.
  }
}

/** Wraps the app; makes `useAuth` available to every route. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [operator, setOperator] = useState<Operator | null>(readStoredOperator);

  const login = useCallback(async (username: string, password: string) => {
    const session = await api.login({ username, password });
    persistSession({ operator: session.operator, token: session.token });
    setOperator(session.operator);
  }, []);

  const logout = useCallback(() => {
    persistSession(null);
    setOperator(null);
    void api.logout();
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ operator, isAuthenticated: operator !== null, login, logout }),
    [operator, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
