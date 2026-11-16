package com.salahlock.app.alarm

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Rejects alarms/reminders from schedules that were replaced after registration. */
object AlarmEventValidator {
    fun isCurrent(deliveredGeneration: Long, activeGeneration: Long): Boolean =
        deliveredGeneration > 0L && deliveredGeneration == activeGeneration

    fun isTimely(triggerAtMs: Long, nowMs: Long, maxDelayMs: Long = 6 * 60 * 60 * 1000L): Boolean =
        triggerAtMs > 0L && triggerAtMs <= nowMs + 60_000L && nowMs - triggerAtMs <= maxDelayMs
}

/** Orders receiver side effects against schedule activation and lifecycle cleanup. */
object PrayerLifecycleSynchronizer {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
