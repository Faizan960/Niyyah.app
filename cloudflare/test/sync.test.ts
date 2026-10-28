import { env } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import type { Env } from "../src/types";
import { api, migrateOnce, mintToken, pull, push } from "./helpers";

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

// ---------------------------------------------------------------------------
// Continuation pagination (no-skip guarantee)
// ---------------------------------------------------------------------------

interface PullBodyFull {
  changes: Record<string, Array<Record<string, unknown>>>;
  cursors: Record<string, number>;
  hasMore: Record<string, boolean>;
  continuation: Record<string, string>;
  serverTime: number;
}

describe("sync: keyset continuation pagination", () => {
  // 600 rows pushed in ONE request all receive the SAME server updated_at.
  // With a 500-row page limit, a naive `updated_at >= lastSeen LIMIT 500` pull
  // would silently drop the 100 rows sharing the boundary timestamp. The keyset
  // continuation must deliver all 600 with no skips and no duplicates.
  it("delivers every row sharing a timestamp across pages (no skips)", async () => {
    const token = await mintToken("page_user");
    // hadith_reads natural key is (collection, hadith_number) — 600 unique rows.
    const hadithRows = Array.from({ length: 600 }, (_, i) => ({
      collection: "bukhari",
      hadith_number: String(i + 1),
      read_at_ms: i,
    }));
    const p = await push(token, { hadith_reads: hadithRows });
    expect(p.status).toBe(200);

    // Page 1
    const r1 = (await (await pull(token, { domains: ["hadith_reads"] })).json()) as PullBodyFull;
    expect(r1.changes.hadith_reads.length).toBe(500);
    expect(r1.hasMore.hadith_reads).toBe(true);
    expect(typeof r1.continuation.hadith_reads).toBe("string");
    // Durable cursor must NOT advance mid-pagination (client keeps old cursor).
    expect(r1.cursors.hadith_reads).toBe(0);

    // Page 2 via continuation
    const r2 = (await (
      await pull(token, { domains: ["hadith_reads"], continuation: { hadith_reads: r1.continuation.hadith_reads } })
    ).json()) as PullBodyFull;
    expect(r2.changes.hadith_reads.length).toBe(100);
    expect(r2.hasMore.hadith_reads).toBe(false);
    // Cursor advances only on the final page.
    expect(r2.cursors.hadith_reads).toBeGreaterThan(0);

    const seen = new Set(
      [...r1.changes.hadith_reads, ...r2.changes.hadith_reads].map((r) => r.hadith_number),
    );
    expect(seen.size).toBe(600); // 600 unique rows — nothing skipped, nothing duplicated
    for (let i = 1; i <= 600; i++) expect(seen.has(String(i))).toBe(true);
  });

  it("rejects a malformed continuation token (400)", async () => {
    const token = await mintToken("page_bad");
    const res = await pull(token, {
      domains: ["hadith_reads"],
      continuation: { hadith_reads: "not-a-token;;;" },
    });
    expect(res.status).toBe(400);
    expect(((await res.json()) as ErrBody).code).toBe("bad_request");
  });

  it("resuming with the durable cursor re-delivers the boundary row (gte, idempotent)", async () => {
    const token = await mintToken("page_gte");
    await push(token, { hadith_reads: [{ collection: "muslim", hadith_number: "1" }] });
    const r1 = (await (await pull(token, { domains: ["hadith_reads"] })).json()) as PullBodyFull;
    const cursor = r1.cursors.hadith_reads;
    // Pulling AT the durable cursor re-delivers the boundary row (idempotent apply).
    const r2 = (await (await pull(token, { domains: ["hadith_reads"], cursors: { hadith_reads: cursor } })).json()) as PullBodyFull;
    expect(r2.changes.hadith_reads.length).toBe(1);
  });
});

// ---------------------------------------------------------------------------
// Local-only / removed-feature state can never be synced
// ---------------------------------------------------------------------------

describe("sync: removed free-pass (mercy) state", () => {
  it("rejects mercy fields in a streaks push (400 forbidden_field)", async () => {
    const token = await mintToken("mercy_field");
    const res = await push(token, {
      streaks: [{ current_streak: 2, best_streak: 5, mercy_used_this_week: true }],
    });
    expect(res.status).toBe(400);
    expect(((await res.json()) as ErrBody).code).toBe("forbidden_field");
  });

  it("rejects last_mercy_week in a streaks push (400 forbidden_field)", async () => {
    const token = await mintToken("mercy_week");
    const res = await push(token, {
      streaks: [{ current_streak: 2, last_mercy_week: "2026-W43" }],
    });
    expect(res.status).toBe(400);
    expect(((await res.json()) as ErrBody).code).toBe("forbidden_field");
  });

  it("rejects mercy/free-pass/monthly-exception domain aliases (400 forbidden_domain)", async () => {
    const token = await mintToken("mercy_domains");
    for (const domain of ["mercy_weeks", "mercy", "free_passes", "monthly_exceptions"]) {
      const res = await push(token, { [domain]: [{ x: 1 }] });
      expect(res.status).toBe(400);
      expect(((await res.json()) as ErrBody).code).toBe("forbidden_domain");
    }
  });

  it("has no mercy columns in the streaks schema", async () => {
    const { results } = await db
      .prepare("PRAGMA table_info(streaks)")
      .all<{ name: string }>();
    const names = results.map((r) => r.name);
    expect(names).not.toContain("last_mercy_week");
    expect(names).not.toContain("mercy_used_this_week");
  });
});

// ---------------------------------------------------------------------------
// Semantic input validation
// ---------------------------------------------------------------------------

describe("sync: semantic validation", () => {
  it("rejects an unknown prayer_name (400)", async () => {
    const token = await mintToken("val_prayer");
    const res = await push(token, {
      prayer_records: [{ date: "2026-10-01", prayer_name: "TEHRUD" }],
    });
    expect(res.status).toBe(400);
  });

  it("rejects a malformed date (400)", async () => {
    const token = await mintToken("val_date");
    const res = await push(token, {
      prayer_records: [{ date: "01/10/2026", prayer_name: "FAJR" }],
    });
    expect(res.status).toBe(400);
  });

  it("rejects an out-of-range surah_number (400)", async () => {
    const token = await mintToken("val_surah");
    const res = await push(token, { quran_bookmarks: [{ surah_number: 115, ayah_number: 1 }] });
    expect(res.status).toBe(400);
  });

  it("rejects an out-of-range ayah_number (400)", async () => {
    const token = await mintToken("val_ayah");
    const res = await push(token, { quran_bookmarks: [{ surah_number: 1, ayah_number: 287 }] });
    expect(res.status).toBe(400);
  });

  it("rejects text fields exceeding their length cap (400)", async () => {
    const token = await mintToken("val_len");
    const res = await push(token, {
      hadith_reads: [{ collection: "b".repeat(65), hadith_number: "1" }],
    });
    expect(res.status).toBe(400);
  });

  it("rejects a non-object row (400)", async () => {
    const token = await mintToken("val_row");
    const res = await push(token, { hadith_reads: ["i-am-not-an-object"] });
    expect(res.status).toBe(400);
  });

  it("rejects an explicitly non-JSON content type (415)", async () => {
    const token = await mintToken("val_ct");
    const res = await api("/api/v1/sync/push", {
      method: "POST",
      token,
      headers: { "content-type": "text/plain" },
      body: "changes={}",
    });
    expect(res.status).toBe(415);
  });
});
