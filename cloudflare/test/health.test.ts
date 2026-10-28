import { describe, expect, it } from "vitest";
import { api } from "./helpers";

describe("health", () => {
  it("responds 200 without auth", async () => {
    const res = await api("/api/v1/health");
    expect(res.status).toBe(200);
    const body = (await res.json()) as { ok: boolean; service: string };
    expect(body.ok).toBe(true);
    expect(body.service).toBe("niyyah-backend");
  });

  it("returns 404 for an unknown route", async () => {
    const res = await api("/api/v1/does-not-exist");
    expect(res.status).toBe(404);
  });
});
