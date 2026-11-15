package com.salahlock.app.alarm

/** Rejects alarms/reminders from schedules that were replaced after registration. */
object AlarmEventValidator {
    fun isCurrent(deliveredGeneration: Long, activeGeneration: Long): Boolean =
        deliveredGeneration > 0L && deliveredGeneration == activeGeneration

    fun isTimely(triggerAtMs: Long, nowMs: Long, maxDelayMs: Long = 6 * 60 * 60 * 1000L): Boolean =
        triggerAtMs > 0L && triggerAtMs <= nowMs + 60_000L && nowMs - triggerAtMs <= maxDelayMs
}
