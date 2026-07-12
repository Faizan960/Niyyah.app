package com.salahlock.app.spiritual

import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Maps Gregorian dates to Ramadan using the platform's civil Islamic calendar
 * (android.icu, offline). Approximate ±1 day vs moon sighting — acceptable for
 * a private consistency achievement, never for prayer times.
 *
 * Kept separate from [AchievementEngine] so the engine stays JVM-pure and
 * unit-testable; this class touches android.icu which only exists on-device.
 */
object RamadanResolver {

    /** All days in [from]..[to] (inclusive) that fall inside Ramadan. */
    fun ramadanDates(from: LocalDate, to: LocalDate): Set<LocalDate> {
        if (from.isAfter(to)) return emptySet()
        val cal = IslamicCalendar(ULocale("@calendar=islamic-civil"))
        val zone = ZoneId.systemDefault()
        val result = mutableSetOf<LocalDate>()
        var day = from
        while (!day.isAfter(to)) {
            cal.time = Date.from(day.atStartOfDay(zone).toInstant())
            if (cal.get(IslamicCalendar.MONTH) == IslamicCalendar.RAMADAN) result.add(day)
            day = day.plusDays(1)
        }
        return result
    }
}
