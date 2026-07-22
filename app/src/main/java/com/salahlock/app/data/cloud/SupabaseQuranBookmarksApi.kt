package com.salahlock.app.data.cloud

import com.salahlock.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * BM-013 Checkpoint C — one row of `public.quran_bookmarks` as sent to / received
 * from Supabase PostgREST. `ayah_number == 0` encodes a whole-surah bookmark
 * (Android's `ayahNumber == null`); the natural key never involves SQL NULLs.
 *
 * `clerk_user_id` is deliberately ABSENT: the server derives it from the verified
 * Clerk JWT (`auth.jwt()->>'sub'` default + RLS), so a client can never assert
 * ownership. `updated_at` is server-authoritative (trigger) and read-only here.
 */
@Serializable
data class CloudBookmarkRow(
    @SerialName("surah_number") val surahNumber: Int,
    @SerialName("ayah_number") val ayahNumber: Int = 0,
    @SerialName("collection_name") val collectionName: String = "",
    @SerialName("created_at_ms") val createdAtMs: Long = 0L,
    /** Non-null = tombstone. Client clock on push (ordering still uses server updated_at). */
    @SerialName("deleted_at") val deletedAt: String? = null,
    /** Server-set; present on pulls, never sent meaningfully on pushes. */
    @SerialName("updated_at") val updatedAt: String = "",
)

/**
 * Write projection for upserts — ONLY the client-writable columns. Deliberately
 * omits `updated_at` (trigger), `created_at`/`id` (defaults) and `clerk_user_id`
 * (RLS-derived from the JWT). Sending an empty `updated_at` string makes Postgres
 * reject the row (`22007 invalid timestamp`), so the read model [CloudBookmarkRow]
 * must never be serialized directly on a push.
 */
@Serializable
private data class CloudBookmarkWrite(
    @SerialName("surah_number") val surahNumber: Int,
    @SerialName("ayah_number") val ayahNumber: Int = 0,
    @SerialName("collection_name") val collectionName: String = "",
    @SerialName("created_at_ms") val createdAtMs: Long = 0L,
    /** Non-null = tombstone; explicit null clears any existing tombstone on re-add. */
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/** Retryable cloud failure (network / 5xx / auth). The outbox event survives. */
class CloudSyncException(message: String, val code: Int = 0, cause: Throwable? = null) :
    IOException(message, cause)

/**
 * Cloud transport for Quran bookmarks — the seam faked by tests. Implementations
 * must be idempotent: `upsert` merges on the natural key, `pull` is a read.
 */
interface QuranBookmarksCloud {
    suspend fun upsert(token: String, rows: List<CloudBookmarkRow>)
    /** Rows with `updated_at >= sinceIso` (all rows when null), ordered by updated_at asc. */
    suspend fun pull(token: String, sinceIso: String?): List<CloudBookmarkRow>
}

/**
 * BM-013 Checkpoint C — the ONLY production Supabase access point (PostgREST over
 * OkHttp; no heavy SDK, per the Checkpoint A proof). UI/ViewModels never call this
 * directly — only the sync engine does, with a fresh Clerk session JWT per run.
 *
 * Security: the `sb_publishable_` key + project URL are public/client-safe config
 * (BuildConfig). The Clerk JWT is sent only as the Bearer header and is NEVER
 * logged, persisted, or embedded in URLs.
 */
class SupabaseQuranBookmarksApi(
    private val baseUrl: String = BuildConfig.SUPABASE_URL,
    private val apiKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    client: OkHttpClient? = null,
) : QuranBookmarksCloud {

    private val http = client ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }

    private val endpoint get() = "$baseUrl/rest/v1/quran_bookmarks"

    val isConfigured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    override suspend fun upsert(token: String, rows: List<CloudBookmarkRow>) {
        if (rows.isEmpty()) return
        // Bulk upsert merging on the natural key; the outbox coalesces to one op per
        // key, so the payload never contains natural-key duplicates.
        val url = "$endpoint?on_conflict=clerk_user_id,surah_number,ayah_number"
        // Project to the write model so the server-managed `updated_at` is never sent
        // (an empty string would fail as an invalid timestamp).
        val writes = rows.map {
            CloudBookmarkWrite(it.surahNumber, it.ayahNumber, it.collectionName, it.createdAtMs, it.deletedAt)
        }
        val body = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(CloudBookmarkWrite.serializer()),
            writes,
        )
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("apikey", apiKey)
            .header("Authorization", "Bearer $token")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .build()
        execute(request) { }
    }

    override suspend fun pull(token: String, sinceIso: String?): List<CloudBookmarkRow> {
        // The watermark is a server timestamp like `2026-07-22T04:49:46.100346+00:00`.
        // Its `+` offset MUST be percent-encoded, or the query string decodes `+` to a
        // space and Postgres rejects the value (22007), stalling every incremental pull.
        val filter = sinceIso?.let { "&updated_at=gte." + it.replace("+", "%2B") } ?: ""
        val url = "$endpoint?select=surah_number,ayah_number,collection_name,created_at_ms,deleted_at,updated_at" +
            "&order=updated_at.asc&limit=10000$filter"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("apikey", apiKey)
            .header("Authorization", "Bearer $token")
            .build()
        return execute(request) { bodyText ->
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(CloudBookmarkRow.serializer()),
                bodyText,
            )
        }
    }

    private suspend fun <T> execute(request: Request, parse: (String) -> T): T =
        withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        // Error bodies may echo row data but never the JWT; keep short.
                        val detail = response.body?.string().orEmpty().take(300)
                        throw CloudSyncException(
                            "Supabase HTTP ${response.code}: $detail",
                            code = response.code,
                        )
                    }
                    parse(response.body?.string().orEmpty())
                }
            } catch (e: CloudSyncException) {
                throw e
            } catch (e: IOException) {
                throw CloudSyncException("Network failure: ${e.message}", cause = e)
            }
        }
}
