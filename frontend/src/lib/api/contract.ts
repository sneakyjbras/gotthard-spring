import type { CustomerSummary, LoginRequest, Session } from './types';

/**
 * Raised by an `ApiClient` implementation for an expected, user-facing
 * failure (bad credentials, no match) as opposed to a network/programming
 * error. Pages can catch this specifically to show its `message` directly.
 */
export class ApiError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * The operator console's one boundary to the backend — a port, in the same
 * sense the backend's `ai/` package defines `LlmClient` as a port in front
 * of a Claude adapter and a stub adapter.
 *
 * No page calls `fetch` directly; every page calls a method here, imported
 * from `./client`. Today `./client` binds this interface to `mockApiClient`.
 * Wiring the real backend later means writing one adapter — an
 * `httpApiClient` implementing `ApiClient` against `fetch` — and changing
 * the single binding in `./client`; nothing that calls `api.*` changes.
 */
export interface ApiClient {
  login(request: LoginRequest): Promise<Session>;
  logout(): Promise<void>;
  /** Empty query returns no results — search is explicit, not a full listing. */
  searchCustomers(query: string): Promise<CustomerSummary[]>;
}
