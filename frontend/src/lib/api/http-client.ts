import { ApiError, NetworkError, SessionExpiredError, type ApiClient } from './contract';
import { notifySessionExpired } from './session-events';
import type {
  ActivityOverview,
  ActivityWindowParams,
  Customer,
  CustomerRiskReport,
  CustomerSummary,
  LoginRequest,
  Operator,
} from './types';

/**
 * The real backend adapter — implements `ApiClient` (see `./contract`)
 * against `fetch`, talking to Spring at `http://localhost:8080` through
 * Vite's dev proxy (`vite.config.ts` forwards `/api/**` there, so requests
 * are same-origin from the browser's point of view and the session cookie's
 * `SameSite=Strict` is satisfied without CORS).
 *
 * **Auth.** Session is a cookie (`HttpOnly`, set by `POST /api/auth/login`),
 * so every request goes with `credentials: 'include'`. CSRF stays on
 * server-side as defence in depth (see `SecurityConfig`'s own doc), which
 * means every state-changing request needs the double-submit header:
 * `GET /api/auth/csrf` is called once to obtain the non-`HttpOnly`
 * `XSRF-TOKEN` cookie, and its value is echoed back as `X-XSRF-TOKEN` on
 * every `POST`/`PUT`/`PATCH`/`DELETE` from then on. `ensureCsrfCookie`
 * bootstraps that lazily, on the first request that needs it.
 */
const XSRF_COOKIE_NAME = 'XSRF-TOKEN';
const XSRF_HEADER_NAME = 'X-XSRF-TOKEN';
const STATE_CHANGING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

/** A `fetch` that never reached the server (offline, wrong port, backend not started) becomes a `NetworkError`, not an unhandled rejection. */
async function rawFetch(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(path, { ...init, credentials: 'include' });
  } catch {
    throw new NetworkError();
  }
}

let csrfBootstrap: Promise<void> | null = null;

/** Hitting the permits-all `/api/auth/csrf` is what makes Spring Security write the `XSRF-TOKEN` cookie — see `CsrfCookieFilter`. Bootstrapped once and reused across concurrent callers. */
async function ensureCsrfCookie(): Promise<void> {
  if (readCookie(XSRF_COOKIE_NAME)) return;
  csrfBootstrap ??= rawFetch('/api/auth/csrf', { method: 'GET' })
    .then(() => undefined)
    .finally(() => {
      csrfBootstrap = null;
    });
  await csrfBootstrap;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  query?: Readonly<Record<string, string | undefined>>;
  /** `false` only for the login call: a 401 there means a wrong password, not a dead session. */
  sessionSensitive?: boolean;
}

function buildUrl(path: string, query: RequestOptions['query']): string {
  const url = new URL(path, window.location.origin);
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined) url.searchParams.set(key, value);
  }
  return url.pathname + url.search;
}

async function requestHeaders(method: string, hasBody: boolean): Promise<HeadersInit> {
  const headers: Record<string, string> = {};
  if (hasBody) headers['Content-Type'] = 'application/json';
  if (STATE_CHANGING_METHODS.has(method)) {
    await ensureCsrfCookie();
    const csrfToken = readCookie(XSRF_COOKIE_NAME);
    if (csrfToken) headers[XSRF_HEADER_NAME] = csrfToken;
  }
  return headers;
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { error?: string };
    return body.error ?? `Request failed (${response.status}).`;
  } catch {
    return `Request failed (${response.status}).`;
  }
}

async function handleResponse<T>(response: Response, sessionSensitive: boolean): Promise<T> {
  if (response.status === 204) return undefined as T;
  if (response.ok) return (await response.json()) as T;

  const message = await errorMessage(response);
  if (response.status === 401 && sessionSensitive) {
    notifySessionExpired();
    throw new SessionExpiredError(message);
  }
  throw new ApiError(message, response.status);
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers = await requestHeaders(method, options.body !== undefined);
  const response = await rawFetch(buildUrl(path, options.query), {
    method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  return handleResponse<T>(response, options.sessionSensitive ?? true);
}

const toCustomerSummary = (customer: Customer): CustomerSummary => ({ ...customer, latestRiskLevel: null });

export const httpApiClient: ApiClient = {
  async login({ username, password }: LoginRequest): Promise<Operator> {
    return request<Operator>('/api/auth/login', { method: 'POST', body: { username, password }, sessionSensitive: false });
  },

  async logout(): Promise<void> {
    await request<void>('/api/auth/logout', { method: 'POST' });
  },

  async getSession(): Promise<Operator | null> {
    try {
      return await request<Operator>('/api/auth/me');
    } catch (error) {
      if (error instanceof SessionExpiredError) return null;
      throw error;
    }
  },

  /**
   * The backend has no bulk/name search today — `GET /api/customers/{idOrReference}` only resolves
   * one exact reference or UUID (see `CustomerSearchService`). This adapts that to the list shape the
   * port promises: a match becomes a single-item result, a 404 becomes no results, anything else
   * (backend down, 500) still throws so the search page can say so.
   */
  async searchCustomers(query: string): Promise<CustomerSummary[]> {
    const trimmed = query.trim();
    if (trimmed.length === 0) return [];
    try {
      const customer = await request<Customer>(`/api/customers/${encodeURIComponent(trimmed)}`);
      return [toCustomerSummary(customer)];
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) return [];
      throw error;
    }
  },

  async getCustomer(idOrReference: string): Promise<Customer> {
    return request<Customer>(`/api/customers/${encodeURIComponent(idOrReference)}`);
  },

  async getCustomerActivity(customerId: string, window?: ActivityWindowParams): Promise<ActivityOverview> {
    return request<ActivityOverview>(`/api/customers/${encodeURIComponent(customerId)}/activity`, {
      query: { from: window?.from, to: window?.to },
    });
  },

  async getCustomerRisk(customerId: string, window?: ActivityWindowParams): Promise<CustomerRiskReport> {
    return request<CustomerRiskReport>(`/api/customers/${encodeURIComponent(customerId)}/risk`, {
      query: { from: window?.from, to: window?.to },
    });
  },
};
