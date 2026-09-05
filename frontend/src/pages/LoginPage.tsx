import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate, type Location } from 'react-router-dom';
import { Button, labelClasses, TextInput } from '@/components/primitives';
import { ApiError } from '@/lib/api/contract';
import { useAuth } from '@/lib/auth/useAuth';

interface LocationState {
  from?: Location;
}

export function LoginPage() {
  const { isAuthenticated, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (isAuthenticated) {
    const redirectTo = (location.state as LocationState | null)?.from?.pathname ?? '/customers';
    return <Navigate to={redirectTo} replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(username, password);
      navigate('/customers', { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Something went wrong. Try again.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="grid min-h-svh grid-cols-1 lg:grid-cols-12">
      <section className="flex flex-col justify-between gap-16 border-rule px-6 py-12 sm:px-10 lg:col-span-7 lg:border-r lg:px-16 lg:py-16">
        <span className="text-sm font-bold tracking-widest uppercase">Gotthard Spring</span>
        <div>
          <p className={labelClasses}>Operator console</p>
          <h1 className="mt-4 text-4xl leading-none font-black tracking-tight sm:text-5xl">
            The way
            <br />
            through the
            <br />
            mountain.
          </h1>
        </div>
        <p className="max-w-md text-sm text-ink-muted">
          Card, payment and cryptocurrency activity for every customer, with a rule-based risk score
          and an AI-written assessment behind it.
        </p>
      </section>

      <section className="flex flex-col justify-center px-6 py-12 sm:px-10 lg:col-span-5 lg:px-16">
        <form onSubmit={handleSubmit} noValidate className="flex w-full max-w-sm flex-col gap-8">
          <div>
            <p className={labelClasses}>Sign in</p>
            <h2 className="mt-2 text-2xl font-bold">Operator access</h2>
          </div>

          <div className="flex flex-col gap-6">
            <TextInput
              label="Username"
              autoComplete="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              monospace
              required
            />
            <TextInput
              label="Password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </div>

          {error ? (
            <p role="alert" className="text-sm text-accent-fg">
              {error}
            </p>
          ) : null}

          <Button type="submit" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </Button>

          <p className="text-sm text-ink-faint">
            Demo — username <span className="font-mono">operator</span>, password{' '}
            <span className="font-mono">gotthard</span>.
          </p>
        </form>
      </section>
    </div>
  );
}
