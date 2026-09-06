/**
 * A tiny publish point between the HTTP client and the auth layer.
 *
 * `http-client.ts` discovers a session has died (a 401 on a call that was
 * not the login attempt itself) deep inside a `fetch`, with no reference to
 * `AuthProvider` — giving it one would invert the dependency the `ApiClient`
 * port exists to avoid. `AuthProvider` is the one subscriber: it registers a
 * handler on mount that clears the signed-in operator, which is all
 * `ProtectedRoute` needs to drop back to `/login`.
 */
let handler: (() => void) | null = null;

/** Called by an `ApiClient` implementation when it detects an expired session. A no-op if nothing is registered. */
export function notifySessionExpired(): void {
  handler?.();
}

/** Registers the one subscriber (`AuthProvider`). Pass `null` to unregister. */
export function setSessionExpiredHandler(next: (() => void) | null): void {
  handler = next;
}
