import { Link } from 'react-router-dom';
import { Button } from '@/components/primitives';
import { useAuth } from '@/lib/auth/useAuth';
import { ThemeToggle } from './ThemeToggle';

/**
 * The page shell's header: wordmark, theme control, and — once
 * authenticated — the operator's identity and a way to sign out. The
 * hairline rule beneath it is the primitive's one separator.
 */
export function Header() {
  const { operator, isAuthenticated, logout } = useAuth();

  return (
    <header className="border-b border-rule">
      <div className="mx-auto flex h-20 w-full max-w-[var(--container-max)] items-center justify-between px-6 sm:px-10">
        <Link to="/" className="text-sm font-bold tracking-widest text-ink uppercase">
          Gotthard Spring
        </Link>
        <div className="flex items-center gap-6">
          <ThemeToggle />
          {isAuthenticated ? (
            <>
              <span className="hidden font-mono text-sm text-ink-muted sm:inline">{operator?.displayName}</span>
              <Button variant="ghost" size="sm" onClick={logout}>
                Sign out
              </Button>
            </>
          ) : null}
        </div>
      </div>
    </header>
  );
}
