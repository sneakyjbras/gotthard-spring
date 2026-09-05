import { createContext } from 'react';
import type { Operator } from '../api/types';

export interface AuthContextValue {
  operator: Operator | null;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
