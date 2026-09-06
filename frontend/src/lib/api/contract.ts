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
 * Raised by an `ApiClient` implementation for an expected, user-facing
 * failure (bad credentials, no match) as opposed to a network/programming
 * error. Pages can catch this specifically to show its `message` directly.
 *
 * `status` is the HTTP status code that produced it, when there is one —
 * absent for `NetworkError`, where no response ever arrived. Most pages only
 * need `message`; a caller that must tell "not found" from "server error"
 * (search, which turns a 404 into an empty result rather than a page error)
 * branches on `status`.
 */
export class ApiError extends Error {
  readonly status?: number;

  constructor(message: string, status?: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/**
 * The session cookie is missing or no longer valid. Distinct from a bad
 * password at login — that is a plain `ApiError` — this fires on any *other*
 * authenticated call once a session has lapsed, so the app can drop back to
 * the login screen instead of rendering a page with no data.
 */
export class SessionExpiredError extends ApiError {
  constructor(message = 'Your session has expired. Sign in again.') {
    super(message, 401);
    this.name = 'SessionExpiredError';
  }
}

/** The request never reached the server at all — wrong port, backend not started, offline. */
export class NetworkError extends ApiError {
  constructor(message = 'Cannot reach the backend. Is it running on http://localhost:8080?') {
    super(message);
    this.name = 'NetworkError';
  }
}

/**
 * The operator console's one boundary to the backend — a port, in the same
 * sense the backend's `ai/` package defines `LlmClient` as a port in front
 * of a Claude adapter and a stub adapter.
 *
 * No page calls `fetch` directly; every page calls a method here, imported
 * from `./client`. `./client` binds this interface to either `httpApiClient`
 * (`./http-client`, the real backend) or `mockApiClient` (`./mock-client`),
 * chosen by an environment variable — see `./client` for which.
 */
export interface ApiClient {
  /** Resolves with the signed-in operator, or throws `ApiError` for a wrong username/password. */
  login(request: LoginRequest): Promise<Operator>;
  logout(): Promise<void>;
  /** The operator behind the current session, or `null` if there is none — never throws for "not signed in". */
  getSession(): Promise<Operator | null>;
  /** Empty query returns no results — search is explicit, not a full listing. */
  searchCustomers(query: string): Promise<CustomerSummary[]>;
  /** By UUID or printed reference (`"CH-4410-8821"`) — whichever an operator has to hand. */
  getCustomer(idOrReference: string): Promise<Customer>;
  /** `customerId` must be the UUID; a reference is not accepted here — resolve it via `getCustomer` first. */
  getCustomerActivity(customerId: string, window?: ActivityWindowParams): Promise<ActivityOverview>;
  getCustomerRisk(customerId: string, window?: ActivityWindowParams): Promise<CustomerRiskReport>;
}
