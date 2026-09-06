import { fileURLToPath } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const srcDir = fileURLToPath(new URL('./src', import.meta.url))

// The backend `./start.sh` brings up (see repo root).
const BACKEND_ORIGIN = 'http://localhost:8080'

// Forwards `/api/**` to the backend so the browser sees one origin for both
// the SPA and the API — the session cookie is `SameSite=Strict` (see
// `SecurityConfig`'s own doc) and this is what satisfies that without CORS
// entering the picture at all. `changeOrigin` rewrites the Host header the
// backend sees to match its own origin, which is what a same-origin proxy
// should look like from the server's side too.
const apiProxy = {
  '/api': { target: BACKEND_ORIGIN, changeOrigin: true },
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': srcDir,
    },
  },
  server: {
    proxy: apiProxy,
  },
  preview: {
    proxy: apiProxy,
  },
  build: {
    // Keep the operator console's initial payload lean; fail loudly rather than
    // silently shipping a bloated bundle as the app grows.
    chunkSizeWarningLimit: 600,
  },
})
