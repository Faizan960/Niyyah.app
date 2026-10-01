import { env } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import type { Env } from "../src/types";
import { migrateOnce, mintToken, pull, push } from "./helpers";

migrateOnce();

const db = (env as unknown as Env).DB;

// Minimal shapes for reading JSON responses.
interface PullBody {
  changes: Record<string, Array<Record<string, unknown>>>;
  cursors: Record<string, number>;
  serverTime: number;
}
interface ErrBody {
  error: string;
  code: string | null;
}

describe("sync: push + pull roundtrip", () => {
  it("stores a pushed row and returns it on pull", async () => {
    const token = await mintToken("user_round");
    const p = await push(token, {
      prayer_records: [
        { date: "2026-10-01", prayer_name: "FAJR", verified: true, timestamp_ms: 123 },
      ],
    });
    expect(p.status).toBe(200);

    const r = await pull(token, { domains: ["prayer_records"] });
    expect(r.status).toBe(200);
    const body = (await r.json()) as PullBody;
    const rows = body.changes.prayer_records;
    expect(rows.length).toBe(1);
    expect(rows[0].prayer_name).toBe("FAJR");
    expect(rows[0].verified).toBe(1);
    expect(rows[0].timestamp_ms).toBe(123);
    expect(body.cursors.prayer_records).toBeGreaterThan(0);
  });

  it("push + pull across multiple domains in one request", async () => {
    const token = await mintToken("user_multi");
    await push(token, {
      prayer_records: [{ date: "2026-12-01", prayer_name: "FAJR" }],
      quran_bookmarks: [{ surah_number: 1, ayah_number: 1 }],
      streaks: [{ current_streak: 3, best_streak: 9 }],
    });
    // No `domains` -> pull everything the user has.
    const r = await pull(token, {});
    const body = (await r.json()) as PullBody;
    expect(body.changes.prayer_records.length).toBe(1);
    expect(body.changes.quran_bookmarks.length).toBe(1);
    expect(body.changes.streaks.length).toBe(1);
    expect(body.changes.streaks[0].current_streak).toBe(3);
  });
});

describe("sync: idempotency", () => {
  it("upserts on the natural key — pushing twice keeps one row with the latest value", async () => {
    const token = await mintToken("user_idem");
    await push(token, {
      quran_bookmarks: [{ surah_number: 2, ayah_number: 255, collection_name: "A" }],
    });
    await push(token, {
      quran_bookmarks: [{ surah_number: 2, ayah_number: 255, collection_name: "B" }],
    });
    const r = await pull(token, { domains: ["quran_bookmarks"] });
    const rows = ((await r.json()) as PullBody).changes.quran_bookmarks;
    expect(rows.length).toBe(1);
    expect(rows[0].collection_name).toBe("B");
  });
});

describe("sync: isolation + identity", () => {
  it("isolates data between users", async () => {
    const a = await mintToken("iso_a");
    const b = await mintToken("iso_b");
    await push(a, { hadith_reads: [{ collection: "bukhari", hadith_number: "1", read_at_ms: 5 }] });
    const r = await pull(b, { domains: ["hadith_reads"] });
    const rows = ((await r.json()) as PullBody).changes.hadith_reads;
    expect(rows.length).toBe(0);
  });

  it("ignores a spoofed user_id in the body — rows belong to the JWT subject only", async () => {
    const owner = await mintToken("spoof_owner");
    const victim = await mintToken("spoof_victim");
    // Attacker (owner's token) tries to write a row attributed to the victim.
    await push(owner, {
      quran_reader_state: [{ user_id: "spoof_victim", surah_number: 1, last_ayah: 7 }],
    });
    // Victim must not see it...
    const vr = await pull(victim, { domains: ["quran_reader_state"] });
    expect(((await vr.json()) as PullBody).changes.quran_reader_state.length).toBe(0);
    // ...it is owned by the real JWT subject.
    const or = await pull(owner, { domains: ["quran_reader_state"] });
    const rows = ((await or.json()) as PullBody).changes.quran_reader_state;
    expect(rows.length).toBe(1);
    expect(rows[0].last_ayah).toBe(7);
  });
});

describe("sync: validation", () => {
  it("rejects a payload missing a required key column (400)", async () => {
    const token = await mintToken("bad_missing");
    const res = await push(token, { prayer_records: [{ date: "2026-10-01" }] });
    expect(res.status).toBe(400);
    expect(((await res.json()) as ErrBody).code).toBe("bad_request");
  });

  it("rejects a payload with a wrong field type (400)", async () => {
    const token = await mintToken("bad_type");
    const res = await push(token, {
      prayer_records: [{ date: "2026-10-01", prayer_name: 123 }],
    });
    expect(res.status).toBe(400);
    expect(((await res.json()) as ErrBody).code).toBe("bad_request");
  });

  it("rejects an unknown / non-syncable domain (400)", async () => {
    const token = await mintToken("bad_domain");
    const res = await push(token, { not_a_table: [{ x: 1 }] });
    expect(res.status).toBe(400);
    expect(((await res.json()) as ErrBody).code).toBe("unknown_domain");
  });
});

describe("sync: emergency overrides are never syncable", () => {
  it("rejects an emergency_overrides push (400 forbidden_domain)", async () => {
    const token = await mintToken("em_push");
    const res = await push(token, {
      emergency_overrides: [{ month_year: "2026-10", count: 1 }],
    });
    expect(res.status).toBe(400);
    expect(((await res.json()) as ErrBody).code).toBe("forbidden_domain");
  });

  it("rejects an emergency_overrides pull (400 forbidden_domain)", async () => {
    const token = await mintToken("em_pull");
    const res = await pull(token, { domains: ["emergency_overrides"] });
    expect(res.status).toBe(400);
    expect(((await res.json()) as ErrBody).code).toBe("forbidden_domain");
  });

  it("has no emergency_overrides table in the schema", async () => {
    const { results } = await db
      .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='emergency_overrides'")
      .all();
    expect(results.length).toBe(0);
  });
});

describe("sync: injection safety", () => {
  it("treats a SQL-injection string as data; tables are untouched", async () => {
    const token = await mintToken("sqli");
    const evil = "1'); DROP TABLE prayer_records;--";
    await push(token, {
      hadith_bookmarks: [{ collection: evil, hadith_number: evil, collection_name: evil }],
    });
    // The value is stored verbatim as data.
    const r = await pull(token, { domains: ["hadith_bookmarks"] });
    const rows = ((await r.json()) as PullBody).changes.hadith_bookmarks;
    expect(rows.length).toBe(1);
    expect(rows[0].collection).toBe(evil);
    // prayer_records was NOT dropped.
    const { results } = await db
      .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='prayer_records'")
      .all();
    expect(results.length).toBe(1);
    // ...and still works.
    const p = await push(token, { prayer_records: [{ date: "2026-10-02", prayer_name: "ASR" }] });
    expect(p.status).toBe(200);
  });
});

describe("sync: tombstone deletes", () => {
  it("marks a row deleted via deleted:true and surfaces deleted_at on pull", async () => {
    const token = await mintToken("tomb");
    await push(token, { quran_bookmarks: [{ surah_number: 18, ayah_number: 10 }] });
    await push(token, { quran_bookmarks: [{ surah_number: 18, ayah_number: 10, deleted: true }] });
    const r = await pull(token, { domains: ["quran_bookmarks"] });
    const rows = ((await r.json()) as PullBody).changes.quran_bookmarks;
    expect(rows.length).toBe(1);
    expect(rows[0].deleted_at).not.toBeNull();
  });
});

describe("sync: incremental cursor", () => {
  it("re-delivers the boundary row at the cursor (gte) and only newer rows past it", async () => {
    const token = await mintToken("cursor_user");
    await push(token, { prayer_records: [{ date: "2026-11-01", prayer_name: "FAJR" }] });
    const first = await pull(token, { domains: ["prayer_records"] });
    const firstBody = (await first.json()) as PullBody;
    const cursor1 = firstBody.cursors.prayer_records;
    expect(firstBody.changes.prayer_records.length).toBe(1);

    // Guarantee a strictly greater server timestamp for the next write.
    await new Promise((r) => setTimeout(r, 25));
    await push(token, { prayer_records: [{ date: "2026-11-02", prayer_name: "DHUHR" }] });

    // gte semantics: pulling AT the cursor re-delivers the boundary row (idempotent).
    const atBoundary = await pull(token, { cursors: { prayer_records: cursor1 } });
    expect(((await atBoundary.json()) as PullBody).changes.prayer_records.length).toBe(2);

    // Advancing past the cursor returns only the newer row.
    const past = await pull(token, { cursors: { prayer_records: cursor1 + 1 } });
    const rows = ((await past.json()) as PullBody).changes.prayer_records;
    expect(rows.length).toBe(1);
    expect(rows[0].date).toBe("2026-11-02");
  });
});
