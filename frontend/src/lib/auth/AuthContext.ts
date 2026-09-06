import { createContext } from 'react';
import type { Operator } from '../api/types';

export interface AuthContextValue {
  operator: Operator | null;
  isAuthenticated: boolean;
  /** True until the mount-time `GET /api/auth/me` check resolves — see `AuthProvider`. `ProtectedRoute` waits on it rather than bouncing a valid session to `/login` just because the cache was empty. */
  isInitializing: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
