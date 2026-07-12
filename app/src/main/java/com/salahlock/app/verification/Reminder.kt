package com.salahlock.app.verification

enum class ReminderType { QURAN, HADITH, REFLECTION }

data class Reminder(
    val id: Int,
    val type: ReminderType,
    val arabic: String?,
    val translation: String,
    val reference: String,
)
