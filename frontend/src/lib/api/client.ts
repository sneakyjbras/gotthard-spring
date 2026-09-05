import type { ApiClient } from './contract';
import { mockApiClient } from './mock-client';

/**
 * The operator console's one binding to a backend implementation.
 *
 * Every page and hook imports `api` from here — never a client module
 * directly, never `fetch`. Swapping mocks for the real backend is exactly
 * this line: write an `httpApiClient` implementing `ApiClient` (see
 * `./contract`) against `fetch`, and point this constant at it.
 */
export const api: ApiClient = mockApiClient;
