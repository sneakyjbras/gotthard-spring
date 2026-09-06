import type { ApiClient } from './contract';
import { httpApiClient } from './http-client';
import { mockApiClient } from './mock-client';

/**
 * The operator console's one binding to a backend implementation.
 *
 * Every page and hook imports `api` from here — never a client module
 * directly, never `fetch`. Defaults to the real backend
 * (`httpApiClient`, see `./http-client`) at `http://localhost:8080` via
 * Vite's dev proxy, so `npm run dev` talks to a running backend with no
 * further setup.
 *
 * Set `VITE_API_MODE=mock` (see `.env.example`) to bind `mockApiClient`
 * instead and review the UI with no backend running at all — every method
 * still has simulated latency and can fail, so loading and error states are
 * exercised the same way either mode is chosen.
 */
const useMock = import.meta.env.VITE_API_MODE === 'mock';

export const api: ApiClient = useMock ? mockApiClient : httpApiClient;
