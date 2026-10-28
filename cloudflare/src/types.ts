/// <reference types="@cloudflare/workers-types" />

/** Worker environment bindings. */
export interface Env {
  /** D1 database binding (see wrangler.jsonc). */
  DB: D1Database;

  /** Clerk issuer origin, e.g. https://your-app.clerk.accounts.dev */
  CLERK_ISSUER: string;
  /** Clerk JWKS endpoint used to verify RS256 session tokens. */
  CLERK_JWKS_URL: string;
  /** Optional comma-separated allowlist of acceptable `azp` claims. Empty = skip. */
  CLERK_AUTHORIZED_PARTIES?: string;

  /**
   * TEST/DEV ONLY. When set, tokens are verified against this literal JWKS JSON
   * (still a real RS256 signature check) instead of fetching CLERK_JWKS_URL.
   * Never set in production.
   */
  CLERK_JWKS_JSON?: string;

  /** Injected by the test harness only; unused in production. */
  TEST_MIGRATIONS?: unknown;
  /** Test-only private JWK (JSON string) used by tests to mint tokens. Never set in production. */
  TEST_PRIVATE_JWK?: string;
}
