import {
  createLocalJWKSet,
  createRemoteJWKSet,
  jwtVerify,
  type JWTPayload,
  type JWTVerifyGetKey,
} from "jose";
import type { Env } from "../types";
import { HttpError } from "../util/http";

/**
 * Clerk session-token verification.
 *
 * Identity is derived ENTIRELY from the verified JWT `sub` claim. The request body
 * is never trusted for identity. Verification needs only Clerk's PUBLIC JWKS — no
 * secret key is involved.
 */

// Cache the remote JWKS resolver per URL for the lifetime of the isolate.
let cachedUrl: string | undefined;
let cachedRemote: ReturnType<typeof createRemoteJWKSet> | undefined;

function keyResolver(env: Env): JWTVerifyGetKey {
  // DEV/TEST path: a literal JWKS supplied via env. Signatures are still verified
  // cryptographically (RS256); only the KEY SOURCE differs from production.
  if (env.CLERK_JWKS_JSON) {
    return createLocalJWKSet(JSON.parse(env.CLERK_JWKS_JSON));
  }
  if (!env.CLERK_JWKS_URL) {
    throw new HttpError(500, "Server auth not configured", "config");
  }
  if (cachedUrl !== env.CLERK_JWKS_URL || !cachedRemote) {
    cachedUrl = env.CLERK_JWKS_URL;
    cachedRemote = createRemoteJWKSet(new URL(env.CLERK_JWKS_URL));
  }
  return cachedRemote;
}

export interface VerifiedUser {
  readonly userId: string;
  readonly claims: JWTPayload;
}

/**
 * Verifies the `Authorization: Bearer <jwt>` header and returns the Clerk user id.
 * Throws {@link HttpError} 401 on any failure. The token is never logged.
 */
export async function authenticate(req: Request, env: Env): Promise<VerifiedUser> {
  const header = req.headers.get("authorization") ?? "";
  const match = /^Bearer\s+(.+)$/i.exec(header.trim());
  if (!match) {
    throw new HttpError(401, "Missing bearer token", "unauthorized");
  }
  const token = match[1].trim();

  let payload: JWTPayload;
  try {
    const result = await jwtVerify(token, keyResolver(env), {
      issuer: env.CLERK_ISSUER || undefined, // jose enforces exp/nbf automatically
      clockTolerance: 5,
    });
    payload = result.payload;
  } catch {
    // Never echo the token or jose's internal reason.
    throw new HttpError(401, "Invalid or expired token", "unauthorized");
  }

  // Optional authorized-party enforcement.
  const allow = (env.CLERK_AUTHORIZED_PARTIES ?? "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  if (allow.length > 0 && typeof payload.azp === "string" && !allow.includes(payload.azp)) {
    throw new HttpError(401, "Unauthorized party", "unauthorized");
  }

  const sub = payload.sub;
  if (!sub || typeof sub !== "string") {
    throw new HttpError(401, "Token missing subject", "unauthorized");
  }
  return { userId: sub, claims: payload };
}
