import { describe, expect, it } from "vitest";
import { api, migrateOnce, mintToken } from "./helpers";

migrateOnce();

describe("auth", () => {
  it("rejects a request with no bearer token (401)", async () => {
    const res = await api("/api/v1/me");
    expect(res.status).toBe(401);
  });

  it("rejects a malformed / garbage token (401)", async () => {
    const res = await api("/api/v1/me", { token: "not.a.real.jwt" });
    expect(res.status).toBe(401);
  });

  it("rejects an expired token (401)", async () => {
    const token = await mintToken("exp_user", { expired: true });
    const res = await api("/api/v1/me", { token });
    expect(res.status).toBe(401);
  });

  it("rejects a token with the wrong issuer (401)", async () => {
    const token = await mintToken("iss_user", { issuer: "https://evil.example" });
    const res = await api("/api/v1/me", { token });
    expect(res.status).toBe(401);
  });

  it("accepts a valid token and returns the Clerk sub as userId", async () => {
    const token = await mintToken("user_me");
    const res = await api("/api/v1/me", { token });
    expect(res.status).toBe(200);
    const body = (await res.json()) as { userId: string };
    expect(body.userId).toBe("user_me");
  });
});
