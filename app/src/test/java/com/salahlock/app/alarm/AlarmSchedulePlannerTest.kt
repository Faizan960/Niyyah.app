package com.salahlock.app.alarm

import com.salahlock.app.data.model.DailyPrayers
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.service.isDuplicateLockStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AlarmSchedulePlannerTest {
    private val date = LocalDate.of(2026, 11, 15)

    @Test
    fun upcoming_excludesPastAndExactBoundary_andKeepsNextDay() {
        val today = prayers(date)
        val tomorrow = prayers(date.plusDays(1))

        val result = AlarmSchedulePlanner.upcoming(
            listOf(today, tomorrow),
            LocalDateTime.of(date, LocalTime.of(15, 30)),
        )

        assertEquals(PrayerName.MAGHRIB, result.first().prayer)
        assertEquals(date.plusDays(1), result.last().time.toLocalDate())
        assertFalse(result.any { it.prayer == PrayerName.ASR && it.time.toLocalDate() == date })
    }

    @Test
    fun repeatedPlanning_isIdempotentAndHasNoDuplicatePrayerDateIdentity() {
        val input = listOf(prayers(date), prayers(date))
        val now = LocalDateTime.of(date, LocalTime.MIDNIGHT)
        val first = AlarmSchedulePlanner.upcoming(input, now)
        val second = AlarmSchedulePlanner.upcoming(input, now)

        assertEquals(5, first.size)
        assertEquals(first, second)
        assertEquals(
            AlarmSchedulePlanner.fingerprint(first, 30, ZoneId.of("Asia/Kolkata")),
            AlarmSchedulePlanner.fingerprint(second, 30, ZoneId.of("Asia/Kolkata")),
        )
    }

    @Test
    fun scheduleIdentity_doesNotChangeMerelyBecausePrayerTimePassed() {
        val input = listOf(prayers(date))
        val identityBefore = AlarmSchedulePlanner.all(input)
        val identityAfter = AlarmSchedulePlanner.all(input)

        assertNotEquals(
            AlarmSchedulePlanner.upcoming(input, date.atTime(12, 0)),
            AlarmSchedulePlanner.upcoming(input, date.atTime(16, 0)),
        )
        assertEquals(
            AlarmSchedulePlanner.fingerprint(identityBefore, 30, ZoneId.of("Asia/Kolkata")),
            AlarmSchedulePlanner.fingerprint(identityAfter, 30, ZoneId.of("Asia/Kolkata")),
        )
    }

    @Test
    fun changedTriggerOrTimezone_changesScheduleGenerationFingerprint() {
        val original = AlarmSchedulePlanner.upcoming(
            listOf(prayers(date)), LocalDateTime.of(date, LocalTime.MIDNIGHT),
        )
        val changed = original.map {
            if (it.prayer == PrayerName.ASR) it.copy(time = it.time.plusMinutes(1)) else it
        }
        assertNotEquals(
            AlarmSchedulePlanner.fingerprint(original, 30, ZoneId.of("Asia/Kolkata")),
            AlarmSchedulePlanner.fingerprint(changed, 30, ZoneId.of("Asia/Kolkata")),
        )
        assertNotEquals(
            AlarmSchedulePlanner.fingerprint(original, 30, ZoneId.of("Asia/Kolkata")),
            AlarmSchedulePlanner.fingerprint(original, 30, ZoneId.of("UTC")),
        )
    }

    @Test
    fun horizonRollover_keepsGenerationWhenOverlappingTriggersAreUnchanged() {
        val firstWindow = AlarmSchedulePlanner.all(
            listOf(prayers(date), prayers(date.plusDays(1))),
        )
        val rolledWindow = AlarmSchedulePlanner.all(
            listOf(prayers(date.plusDays(1)), prayers(date.plusDays(2))),
        )

        assertTrue(
            AlarmSchedulePlanner.sameGeneration(
                AlarmSchedulePlanner.generationIdentity(firstWindow, 30, ZoneId.of("Asia/Kolkata")),
                AlarmSchedulePlanner.generationIdentity(rolledWindow, 30, ZoneId.of("Asia/Kolkata")),
            ),
        )
    }

    @Test
    fun overlappingTriggerChange_advancesGeneration() {
        val original = AlarmSchedulePlanner.all(
            listOf(prayers(date), prayers(date.plusDays(1))),
        )
        val changed = original.map {
            if (it.prayer == PrayerName.ISHA && it.time.toLocalDate() == date.plusDays(1)) {
                it.copy(time = it.time.plusMinutes(1))
            } else it
        }

        assertFalse(
            AlarmSchedulePlanner.sameGeneration(
                AlarmSchedulePlanner.generationIdentity(original, 30, ZoneId.of("Asia/Kolkata")),
                AlarmSchedulePlanner.generationIdentity(changed, 30, ZoneId.of("Asia/Kolkata")),
            ),
        )
    }

    @Test
    fun sourceAndJamaatEdits_produceDistinctRefreshRequests() {
        assertNotEquals(
            refreshTriggerKey("coords|method", "API", 1L),
            refreshTriggerKey("coords|method", "LOCAL_MASJID", 1L),
        )
        assertNotEquals(
            refreshTriggerKey("coords|method", "LOCAL_MASJID", 1L),
            refreshTriggerKey("coords|method", "LOCAL_MASJID", 2L),
        )
    }

    @Test
    fun staleEventsAndDuplicateServiceStartsAreRejected() {
        assertTrue(AlarmEventValidator.isCurrent(4L, 4L))
        assertFalse(AlarmEventValidator.isCurrent(0L, 4L))
        assertFalse(AlarmEventValidator.isCurrent(3L, 4L))
        assertTrue(AlarmEventValidator.isTimely(1_000L, 1_500L))
        assertFalse(AlarmEventValidator.isTimely(1_000L, 30_000_000L))
        assertTrue(
            isDuplicateLockStart(
                active = true,
                currentPrayer = PrayerName.ASR,
                incomingPrayer = PrayerName.ASR,
                currentGeneration = 4L,
                incomingGeneration = 4L,
                currentEndMs = 2_000L,
                nowMs = 1_000L,
            ),
        )
        assertFalse(
            isDuplicateLockStart(true, PrayerName.ASR, PrayerName.MAGHRIB, 4L, 4L, 2_000L, 1_000L),
        )
    }

    @Test
    fun requestCodes_areStableAndUniqueAcrossPrayerAndDate() {
        val asrToday = AlarmScheduler.buildRequestCode(PrayerName.ASR, date)
        assertEquals(asrToday, AlarmScheduler.buildRequestCode(PrayerName.ASR, date))
        assertNotEquals(asrToday, AlarmScheduler.buildRequestCode(PrayerName.MAGHRIB, date))
        assertNotEquals(asrToday, AlarmScheduler.buildRequestCode(PrayerName.ASR, date.plusDays(1)))
    }

    private fun prayers(day: LocalDate) = DailyPrayers(
        date = day.toString(),
        fajr = day.atTime(5, 0),
        sunrise = day.atTime(6, 15),
        dhuhr = day.atTime(12, 30),
        asr = day.atTime(15, 30),
        maghrib = day.atTime(18, 0),
        isha = day.atTime(19, 30),
    )
}
