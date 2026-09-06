import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/lib/auth/useAuth';

/** A bare, wordless hold — shown only for the moment it takes `AuthProvider` to confirm a cached session is still valid, never long enough to need more than this. */
function SessionCheck() {
  return <div className="min-h-svh bg-paper" />;
}

/** Gates its nested routes behind a session; remembers where to return after login. */
export function ProtectedRoute() {
  const { isAuthenticated, isInitializing } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    // A cached operator renders immediately (see AuthProvider); an empty cache waits for the
    // mount-time session check before deciding there is really nobody signed in.
    if (isInitializing) {
      return <SessionCheck />;
    }
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}
