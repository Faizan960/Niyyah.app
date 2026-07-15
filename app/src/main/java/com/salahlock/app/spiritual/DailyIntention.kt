package com.salahlock.app.spiritual

import java.time.LocalDate

/**
 * The day's intention shown on Home. A curated, app-provided library (the same
 * category of bundled content as the Quran / Hadith / Azkar text) rotated
 * deterministically by date, so it is genuinely "today's" intention rather than a
 * single hardcoded string. The voice follows the Master Spec: calm, merciful,
 * never guilt-inducing.
 */
object DailyIntention {

    private val intentions = listOf(
        "To approach every task today with patience and seeking only His pleasure.",
        "To guard my tongue and speak only what is good, or to remain silent.",
        "To meet each person I encounter today with gentleness and a soft heart.",
        "To perform my prayers with presence, as though I see Him.",
        "To be grateful for the small mercies I usually overlook.",
        "To give quietly today, seeking no reward but His acceptance.",
        "To forgive those who wronged me, hoping He forgives me in turn.",
        "To lower my gaze and protect my heart from what distracts me from Him.",
        "To begin each task with His name and end it with gratitude.",
        "To seek knowledge today that draws me nearer to my Lord.",
        "To be honest in my dealings, even when honesty is difficult.",
        "To remember death gently, so I may live this day with intention.",
        "To care for my parents today with mercy and a lowered wing.",
        "To turn to Him first in my worry, before I turn to anyone else.",
        "To spend my time today in what benefits my faith and my afterlife.",
        "To hold firmly to patience in whatever this day brings.",
        "To make my home a place of peace and remembrance.",
        "To seek His forgiveness often, for He loves those who return to Him.",
        "To be a means of ease for someone struggling today.",
        "To eat, work and rest in moderation, as a trust from Him.",
        "To read even a little of His words today, and reflect upon them.",
        "To keep my promises and honour the trust others place in me.",
        "To let go of anger for His sake, and answer harshness with calm.",
        "To end this day having drawn one step closer to Allah.",
    )

    /** The intention for [date] (defaults to today). Deterministic and stable per day. */
    fun forDate(date: LocalDate = LocalDate.now()): String {
        val index = ((date.toEpochDay() % intentions.size) + intentions.size) % intentions.size
        return intentions[index.toInt()]
    }
}
