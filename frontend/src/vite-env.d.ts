/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** `'mock'` binds `api` (`src/lib/api/client.ts`) to the mock client; anything else, or unset, binds the real backend. */
  readonly VITE_API_MODE?: 'mock' | 'real';
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
