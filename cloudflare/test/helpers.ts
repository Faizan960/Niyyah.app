import { applyD1Migrations, env, SELF } from "cloudflare:test";
import { importJWK, SignJWT, type JWK } from "jose";
import { beforeAll } from "vitest";
import type { Env } from "../src/types";

const e = env as unknown as Env;

/** Applies the real D1 migrations to the test database once per file. */
export function migrateOnce(): void {
  beforeAll(async () => {
    await applyD1Migrations(e.DB, e.TEST_MIGRATIONS as Parameters<typeof applyD1Migrations>[1]);
  });
}

export interface MintOpts {
  expired?: boolean;
  issuer?: string;
  azp?: string;
}

/** Mints a Clerk-shaped RS256 session token for `sub` using the test private key. */
export async function mintToken(sub: string, opts: MintOpts = {}): Promise<string> {
  const privJwk = JSON.parse(e.TEST_PRIVATE_JWK as string) as JWK;
  const key = await importJWK(privJwk, "RS256");
  const nowSec = Math.floor(Date.now() / 1000);
  const payload: Record<string, unknown> = {};
  if (opts.azp) payload.azp = opts.azp;
  return new SignJWT(payload)
    .setProtectedHeader({ alg: "RS256", kid: privJwk.kid })
    .setSubject(sub)
    .setIssuer(opts.issuer ?? (e.CLERK_ISSUER as string))
    .setIssuedAt(opts.expired ? nowSec - 3600 : nowSec)
    .setExpirationTime(opts.expired ? nowSec - 1800 : nowSec + 3600)
    .sign(key);
}

interface ApiInit {
  method?: string;
  token?: string;
  body?: unknown;
  headers?: Record<string, string>;
}

/** Calls the Worker under test over its real fetch handler. */
export async function api(path: string, init: ApiInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  if (init.token) headers.set("authorization", `Bearer ${init.token}`);
  let body: string | undefined;
  if (init.body !== undefined) {
    body = typeof init.body === "string" ? init.body : JSON.stringify(init.body);
    if (!headers.has("content-type")) headers.set("content-type", "application/json");
  }
  return SELF.fetch(`https://niyyah.test${path}`, { method: init.method ?? "GET", headers, body });
}

export function push(token: string, changes: Record<string, unknown[]>): Promise<Response> {
  return api("/api/v1/sync/push", { method: "POST", token, body: { changes } });
}

export function pull(token: string, body: Record<string, unknown> = {}): Promise<Response> {
  return api("/api/v1/sync/pull", { method: "POST", token, body });
}
