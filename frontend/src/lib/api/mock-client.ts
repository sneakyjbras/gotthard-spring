import { ApiError, type ApiClient } from './contract';
import { mockCustomers } from './mock-data';
import type { CustomerSummary, LoginRequest, Session } from './types';

/** The only account this build accepts — stated on the login page itself. */
const DEMO_USERNAME = 'operator';
const DEMO_PASSWORD = 'gotthard';

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

/**
 * Stands in for the real backend. Implements `ApiClient` against the fixed
 * dataset in `./mock-data` instead of `fetch` — every method still has real
 * (simulated) latency and can fail, so the pages built against this
 * adapter behave the same way once `./client` is repointed at an HTTP one.
 */
export const mockApiClient: ApiClient = {
  async login({ username, password }: LoginRequest): Promise<Session> {
    await networkDelay();
    if (normalize(username) !== DEMO_USERNAME || password !== DEMO_PASSWORD) {
      throw new ApiError('Incorrect username or password.');
    }
    return {
      operator: {
        operatorId: crypto.randomUUID(),
        username: DEMO_USERNAME,
        displayName: 'A. Keller',
        role: 'OPERATOR',
      },
      token: crypto.randomUUID(),
      issuedAt: new Date().toISOString(),
    };
  },

  async logout(): Promise<void> {
    await wait(120);
  },

  async searchCustomers(query: string): Promise<CustomerSummary[]> {
    await networkDelay();
    const trimmed = query.trim();
    if (trimmed.length === 0) return [];
    return mockCustomers.filter((customer) => matchesQuery(customer, trimmed));
  },
};
