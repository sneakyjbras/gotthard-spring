import { ApiError, type ApiClient } from './contract';
import { mockActivityOverview, mockCustomers, mockRiskReport } from './mock-data';
import type { ActivityOverview, Customer, CustomerRiskReport, CustomerSummary, LoginRequest, Operator } from './types';

/**
 * The only account this build accepts, in mock mode — the same operator the
 * real backend seeds (`V2__seed_operators.sql`), so the login screen's demo
 * hint is true regardless of which client `./client` is bound to.
 */
const DEMO_OPERATOR: Operator = {
  operatorId: '11111111-1111-1111-1111-111111111111',
  username: 'e.rossi',
  displayName: 'Elena Rossi',
  role: 'OPERATOR',
};
const DEMO_PASSWORD = 'Operator-Demo-2026';

/**
 * This client's own stand-in for the backend's session cookie. Deliberately
 * a key of its own, not `AuthProvider`'s `gotthard.session` cache — the real
 * client's session lives entirely server-side and `AuthProvider` only
 * mirrors it, so the mock needs the same separation to behave the same way
 * (a signed-in reviewer stays signed in across a reload; `getSession` is
 * truthful, not just an echo of whatever `AuthProvider` last wrote).
 */
const MOCK_SESSION_KEY = 'gotthard.mock-session';

function readMockSession(): Operator | null {
  try {
    const raw = localStorage.getItem(MOCK_SESSION_KEY);
    return raw ? (JSON.parse(raw) as Operator) : null;
  } catch {
    return null;
  }
}

function writeMockSession(operator: Operator | null): void {
  try {
    if (operator) {
      localStorage.setItem(MOCK_SESSION_KEY, JSON.stringify(operator));
    } else {
      localStorage.removeItem(MOCK_SESSION_KEY);
    }
  } catch {
    // Best-effort — a private-browsing tab that refuses storage still gets a working session.
  }
}

const wait = (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms));

/** A believable latency band so loading states in the UI are exercised, not imagined. */
const networkDelay = (): Promise<void> => wait(280 + Math.random() * 260);

const normalize = (value: string): string => value.trim().toLowerCase();

function matchesQuery(customer: CustomerSummary, query: string): boolean {
  const needle = normalize(query);
  return (
    normalize(customer.reference).includes(needle) ||
    normalize(customer.fullName).includes(needle) ||
    normalize(customer.customerId).includes(needle)
  );
}

function findCustomer(idOrReference: string): CustomerSummary {
  const needle = idOrReference.trim();
  const found = mockCustomers.find(
    (customer) => customer.customerId === needle || customer.reference.toUpperCase() === needle.toUpperCase(),
  );
  if (!found) {
    throw new ApiError(`No customer with id or reference ${needle}`, 404);
  }
  return found;
}

/**
 * Stands in for the real backend. Implements `ApiClient` against the fixed
 * dataset in `./mock-data` instead of `fetch` — every method still has real
 * (simulated) latency and can fail, so the pages built against this adapter
 * behave the same way `./client` swaps in `httpApiClient`.
 *
 * Unlike the real backend, this client's `searchCustomers` also matches by
 * name — there is no bulk/name search endpoint today (see `http-client.ts`),
 * so the mock is deliberately the friendlier of the two here.
 */
export const mockApiClient: ApiClient = {
  async login({ username, password }: LoginRequest): Promise<Operator> {
    await networkDelay();
    if (normalize(username) !== DEMO_OPERATOR.username || password !== DEMO_PASSWORD) {
      throw new ApiError('Invalid username or password', 401);
    }
    writeMockSession(DEMO_OPERATOR);
    return DEMO_OPERATOR;
  },

  async logout(): Promise<void> {
    await wait(120);
    writeMockSession(null);
  },

  async getSession(): Promise<Operator | null> {
    await wait(80);
    return readMockSession();
  },

  async searchCustomers(query: string): Promise<CustomerSummary[]> {
    await networkDelay();
    const trimmed = query.trim();
    if (trimmed.length === 0) return [];
    return mockCustomers.filter((customer) => matchesQuery(customer, trimmed));
  },

  async getCustomer(idOrReference: string): Promise<Customer> {
    await networkDelay();
    return findCustomer(idOrReference);
  },

  async getCustomerActivity(customerId: string): Promise<ActivityOverview> {
    await networkDelay();
    const customer = mockCustomers.find((candidate) => candidate.customerId === customerId) ?? findCustomer(customerId);
    return mockActivityOverview(customer);
  },

  async getCustomerRisk(customerId: string): Promise<CustomerRiskReport> {
    await networkDelay();
    const customer = mockCustomers.find((candidate) => candidate.customerId === customerId) ?? findCustomer(customerId);
    return mockRiskReport(customer);
  },
};
