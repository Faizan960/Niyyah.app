package com.salahlock.app.data.cloud

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BM-013 Checkpoint C regression: the upsert body must contain ONLY client-writable
 * columns. It previously serialized the read model [CloudBookmarkRow] with
 * `encodeDefaults`, sending `"updated_at":""` — which Postgres rejects as an invalid
 * timestamp (22007), silently stalling all sync. The fake-cloud engine tests could
 * not catch this; only real PostgREST serialization does. This asserts the wire body
 * omits the server-managed column, with no network (an interceptor short-circuits).
 */
class SupabaseQuranBookmarksApiTest {

    private fun apiCapturing(sink: (String) -> Unit): SupabaseQuranBookmarksApi {
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val buffer = Buffer()
            req.body?.writeTo(buffer)
            sink(buffer.readUtf8())
            Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body("".toResponseBody(null))
                .build()
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        return SupabaseQuranBookmarksApi(baseUrl = "https://example.test", apiKey = "k", client = client)
    }

    @Test
    fun upsert_wireBody_omitsServerManagedUpdatedAt() {
        var body: String? = null
        val api = apiCapturing { body = it }
        runBlocking {
            api.upsert("jwt", listOf(CloudBookmarkRow(surahNumber = 1, ayahNumber = 1, createdAtMs = 123L)))
        }
        assertNotNull("request body was captured", body)
        assertFalse("must not send server-managed updated_at", body!!.contains("updated_at"))
        // clerk_user_id is RLS-derived from the JWT and must never be asserted by the client.
        assertFalse("must not send clerk_user_id", body!!.contains("clerk_user_id"))
        assertTrue("sends the natural key", body!!.contains("surah_number") && body!!.contains("ayah_number"))
        assertTrue("sends created_at_ms", body!!.contains("\"created_at_ms\":123"))
    }

    @Test
    fun pull_percentEncodesTimestampOffsetInWatermark() {
        var url: String? = null
        val interceptor = Interceptor { chain ->
            url = chain.request().url.toString()
            Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("[]".toResponseBody(null)).build()
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val api = SupabaseQuranBookmarksApi(baseUrl = "https://example.test", apiKey = "k", client = client)
        runBlocking { api.pull("jwt", sinceIso = "2026-07-22T04:49:46.100346+00:00") }
        assertNotNull(url)
        // The `+` offset must be encoded so it is not decoded to a space server-side.
        assertTrue("offset + is percent-encoded", url!!.contains("gte.2026-07-22T04:49:46.100346%2B00:00"))
        assertFalse("no raw + in the timestamp filter", url!!.contains("100346+00:00"))
    }

    @Test
    fun upsert_tombstone_sendsDeletedAt() {
        var body: String? = null
        val api = apiCapturing { body = it }
        runBlocking {
            api.upsert("jwt", listOf(CloudBookmarkRow(surahNumber = 2, ayahNumber = 255, deletedAt = "2026-07-22T00:00:00Z")))
        }
        assertNotNull(body)
        assertTrue("tombstone carries deleted_at", body!!.contains("deleted_at"))
        assertFalse(body!!.contains("updated_at"))
    }
}
