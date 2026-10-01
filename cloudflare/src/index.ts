import type { Env } from "./types";
import { authenticate } from "./auth/clerk";
import { handlePull, handlePush } from "./db/sync";
import { errorResponse, HttpError, json } from "./util/http";

/**
 * Niyyah backend Worker — versioned JSON API under /api/v1.
 *
 *   GET  /api/v1/health      → liveness (no auth)
 *   GET  /api/v1/me          → the authenticated Clerk user id
 *   POST /api/v1/sync/push   → upsert client changes
 *   POST /api/v1/sync/pull   → cursor-based read of server changes
 *
 * Every route except /health requires a verified Clerk session JWT; identity is
 * taken from the token, never the body.
 */

const MAX_BODY_BYTES = 5 * 1024 * 1024; // 5 MB

async function ensureUser(env: Env, userId: string, now: number): Promise<void> {
  await env.DB.prepare(
    `INSERT INTO users (user_id, created_at, last_seen_at) VALUES (?, ?, ?) ` +
      `ON CONFLICT(user_id) DO UPDATE SET last_seen_at = excluded.last_seen_at`,
  )
    .bind(userId, now, now)
    .run();
}

async function readJson(req: Request): Promise<unknown> {
  const declared = Number(req.headers.get("content-length") ?? "0");
  if (declared > MAX_BODY_BYTES) throw new HttpError(413, "Body too large", "too_large");
  const text = await req.text();
  if (text.length > MAX_BODY_BYTES) throw new HttpError(413, "Body too large", "too_large");
  if (text.length === 0) return {};
  try {
    return JSON.parse(text);
  } catch {
    throw new HttpError(400, "Invalid JSON body", "bad_request");
  }
}

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const { pathname } = new URL(req.url);
    try {
      if (req.method === "GET" && pathname === "/api/v1/health") {
        return json({ ok: true, service: "niyyah-backend", time: Date.now() });
      }

      if (req.method === "GET" && pathname === "/api/v1/me") {
        const { userId } = await authenticate(req, env);
        await ensureUser(env, userId, Date.now());
        return json({ userId });
      }

      if (req.method === "POST" && pathname === "/api/v1/sync/push") {
        const { userId } = await authenticate(req, env);
        const now = Date.now();
        await ensureUser(env, userId, now);
        const result = await handlePush(env, userId, await readJson(req));
        return json(result);
      }

      if (req.method === "POST" && pathname === "/api/v1/sync/pull") {
        const { userId } = await authenticate(req, env);
        await ensureUser(env, userId, Date.now());
        const result = await handlePull(env, userId, await readJson(req));
        return json(result);
      }

      return errorResponse(404, "Not found", "not_found");
    } catch (e) {
      if (e instanceof HttpError) return errorResponse(e.status, e.message, e.code);
      // Unexpected failure — never leak internals or the token.
      return errorResponse(500, "Internal error", "internal");
    }
  },
} satisfies ExportedHandler<Env>;
