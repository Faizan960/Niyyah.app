package com.salahlock.app.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.successOrNull
import com.clerk.api.session.fetchToken
import com.salahlock.app.BuildConfig
import com.salahlock.app.data.cloud.SupabaseQuranBookmarksApi
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.sync.ActiveOwnerProvider
import com.salahlock.app.data.sync.OwnerScope
import com.salahlock.app.data.sync.QuranBookmarkSyncEngine
import java.util.concurrent.TimeUnit

/**
 * BM-013 Checkpoint C — durable background sync for Quran bookmarks.
 *
 * Local mutations NEVER wait for this worker: the Room transaction (mutation +
 * outbox event) commits first and the UI updates immediately; this worker drains
 * the outbox and pulls cloud changes whenever network + an authenticated Clerk
 * session are available.
 *
 *  - Unique work (`APPEND_OR_REPLACE`) → no worker storms; a run scheduled while
 *    one is active chains after it, so mutations landing mid-run are still drained.
 *  - Network constraint + exponential backoff; no foreground service, no polling.
 *  - Signed out / no session → succeed WITHOUT touching the outbox (events are
 *    retained and re-scheduled on the next sign-in or mutation).
 *  - A fresh Clerk JWT is fetched per run via [fetchToken]; it is never persisted
 *    or logged.
 */
class QuranBookmarkSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scope = ActiveOwnerProvider.shared.scope()
        val userId = (scope as? OwnerScope.Authenticated)?.clerkUserId
        if (userId == null) {
            Log.d(TAG, "No authenticated owner — skipping (outbox retained).")
            return Result.success()
        }
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) {
            Log.w(TAG, "Supabase not configured — skipping.")
            return Result.success()
        }
        val engine = QuranBookmarkSyncEngine(
            db = AppDatabase.getInstance(applicationContext),
            cloud = SupabaseQuranBookmarksApi(),
            token = { Clerk.session?.fetchToken()?.successOrNull()?.jwt },
        )
        return try {
            engine.sync(userId)
            Log.d(TAG, "Sync pass complete for authenticated owner.")
            Result.success()
        } catch (e: Exception) {
            // Never log tokens; message is HTTP status/short detail only.
            Log.w(TAG, "Sync failed (attempt $runAttemptCount): ${e.message}")
            if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        private const val TAG = "QuranBookmarkSync"
        private const val UNIQUE_NAME = "quran_bookmark_sync"

        /** Attempts per enqueue before giving up; the outbox survives and the next
         *  mutation / sign-in / app start re-enqueues. */
        private const val MAX_ATTEMPTS = 8

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<QuranBookmarkSyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
