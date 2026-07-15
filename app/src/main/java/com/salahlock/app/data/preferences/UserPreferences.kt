package com.salahlock.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {

    enum class ThemePreference {
        SYSTEM, LIGHT, DARK, AMOLED
    }

    companion object {
        val KEY_THEME_PREFERENCE = stringPreferencesKey("theme_preference")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_CALC_METHOD = stringPreferencesKey("calc_method") // KARACHI | MWL | ISNA
        val KEY_MADHAB = stringPreferencesKey("madhab") // HANAFI | SHAFI
        val KEY_LOCK_DURATION_MIN = intPreferencesKey("lock_duration_min") // 15/30/45/60
        val KEY_FAJR_GENTLE_MODE = booleanPreferencesKey("fajr_gentle_mode")
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_EMAIL_NEWSLETTER = booleanPreferencesKey("email_newsletter_enabled")
        val KEY_ADHAN_ENABLED = booleanPreferencesKey("adhan_enabled")
        // Prayer sync guard — prevents redundant API calls
        val KEY_LAST_PRAYER_SYNC_MS = longPreferencesKey("last_prayer_sync_ms")
        val KEY_LAST_SYNC_LAT = doublePreferencesKey("last_sync_lat")
        val KEY_LAST_SYNC_LNG = doublePreferencesKey("last_sync_lng")
        val KEY_LAST_SYNC_MONTH = stringPreferencesKey("last_sync_month")
        // Missed prayer reminder delay (minutes)
        val KEY_MISSED_PRAYER_DELAY_MIN = intPreferencesKey("missed_prayer_delay_min")
        // Per-prayer lock enable/disable — all on by default
        val KEY_LOCK_FAJR = booleanPreferencesKey("lock_prayer_fajr")
        val KEY_LOCK_DHUHR = booleanPreferencesKey("lock_prayer_dhuhr")
        val KEY_LOCK_ASR = booleanPreferencesKey("lock_prayer_asr")
        val KEY_LOCK_MAGHRIB = booleanPreferencesKey("lock_prayer_maghrib")
        val KEY_LOCK_ISHA = booleanPreferencesKey("lock_prayer_isha")
        // Active block profile name (MINIMAL | BALANCED | STRICT | CUSTOM)
        val KEY_BLOCK_PROFILE = stringPreferencesKey("block_profile")

        // Sprint E.2 — local backup
        val KEY_LAST_BACKUP_MS = longPreferencesKey("last_backup_ms")
        val KEY_AUTO_BACKUP_FREQ = stringPreferencesKey("auto_backup_frequency")

        // Sprint E.1 — local masjid prayer source
        val KEY_PRAYER_SOURCE = stringPreferencesKey("prayer_source")

        // Sprint E — spiritual accountability verification
        val KEY_VERIFICATION_METHOD = stringPreferencesKey("verification_method")
        val KEY_VERIFICATION_CONFIRM_COUNT = intPreferencesKey("verification_confirm_count")
        val KEY_REMINDER_QURAN = booleanPreferencesKey("reminder_quran_enabled")
        val KEY_REMINDER_HADITH = booleanPreferencesKey("reminder_hadith_enabled")
        val KEY_REMINDER_REFLECTION = booleanPreferencesKey("reminder_reflection_enabled")

        // Sprint N.3 — temporary pause of the lock engine (epoch millis; 0 = not paused)
        val KEY_PAUSE_UNTIL_MS = longPreferencesKey("pause_until_ms")

        // Sprint A.1 — last time the daily interstitial ad was shown (epoch millis)
        val KEY_LAST_INTERSTITIAL_MS = longPreferencesKey("last_interstitial_ms")

        // SL-004 — Local Masjid Jumma (Friday) times, "HH:mm" (blank = not set).
        // Stored in DataStore, NOT Room — avoids a schema migration for display-only data.
        val KEY_JUMMA_1 = stringPreferencesKey("local_masjid_jumma1")
        val KEY_JUMMA_2 = stringPreferencesKey("local_masjid_jumma2")
    }

    private val securePreferences = SecurePreferences(context)

    val themePreference: Flow<ThemePreference> = context.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_THEME_PREFERENCE] ?: ThemePreference.SYSTEM.name
            try {
                ThemePreference.valueOf(name)
            } catch (e: Exception) {
                ThemePreference.SYSTEM
            }
        }

    val onboardingDone: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_ONBOARDING_DONE] ?: false }

    val calcMethod: Flow<String> = context.dataStore.data
        .map { it[KEY_CALC_METHOD] ?: "KARACHI" }

    val madhab: Flow<String> = context.dataStore.data
        .map { it[KEY_MADHAB] ?: "HANAFI" }

    val lockDurationMin: Flow<Int> = context.dataStore.data
        .map { it[KEY_LOCK_DURATION_MIN] ?: 30 }

    val fajrGentleMode: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_FAJR_GENTLE_MODE] ?: true }

    // Read directly from SecurePreferences StateFlows
    val userLat: Flow<Double> = securePreferences.userLat
    val userLng: Flow<Double> = securePreferences.userLng
    val cityName: Flow<String> = securePreferences.cityName
    val locationMode: Flow<String> = securePreferences.locationMode

    val adhanEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_ADHAN_ENABLED] ?: true }

    /** Master switch for announcement/reminder notifications (Settings). */
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_NOTIFICATIONS_ENABLED] ?: true }

    /** Email newsletter opt-in (Settings). Off by default — never pre-consented. */
    val emailNewsletterEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_EMAIL_NEWSLETTER] ?: false }

    suspend fun setEmailNewsletterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EMAIL_NEWSLETTER] = enabled }
    }

    /** Lock engine is paused until this epoch-millis. 0 (or past) = not paused. */
    val pauseUntil: Flow<Long> = context.dataStore.data
        .map { it[KEY_PAUSE_UNTIL_MS] ?: 0L }

    /** Epoch-millis the daily interstitial ad was last shown (0 = never). */
    val lastInterstitialShown: Flow<Long> = context.dataStore.data
        .map { it[KEY_LAST_INTERSTITIAL_MS] ?: 0L }

    /** SL-004 — Jumma (Friday) prayer times for the local masjid. Blank = not set. */
    val jumma1: Flow<String> = context.dataStore.data.map { it[KEY_JUMMA_1] ?: "" }
    val jumma2: Flow<String> = context.dataStore.data.map { it[KEY_JUMMA_2] ?: "" }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.dataStore.edit { it[KEY_THEME_PREFERENCE] = theme.name }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = done }
    }
    suspend fun setCalcMethod(method: String) {
        context.dataStore.edit { it[KEY_CALC_METHOD] = method }
    }
    suspend fun setMadhab(madhab: String) {
        context.dataStore.edit { it[KEY_MADHAB] = madhab }
    }
    suspend fun setLockDuration(minutes: Int) {
        context.dataStore.edit { it[KEY_LOCK_DURATION_MIN] = minutes }
    }
    suspend fun setFajrGentleMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FAJR_GENTLE_MODE] = enabled }
    }
    /** Pause the lock engine until [untilMs] (epoch millis). Pass 0 to resume now. */
    suspend fun setPauseUntil(untilMs: Long) {
        context.dataStore.edit { it[KEY_PAUSE_UNTIL_MS] = untilMs }
    }
    /** Records when the daily interstitial ad was shown. */
    suspend fun setLastInterstitialShown(ms: Long) {
        context.dataStore.edit { it[KEY_LAST_INTERSTITIAL_MS] = ms }
    }
    /** SL-004 — saves Jumma times ("HH:mm", blank clears). */
    suspend fun setJummaTimes(jumma1: String, jumma2: String) {
        context.dataStore.edit {
            it[KEY_JUMMA_1] = jumma1.trim()
            it[KEY_JUMMA_2] = jumma2.trim()
        }
    }
    suspend fun setLocation(lat: Double, lng: Double, city: String, mode: String) {
        securePreferences.setLocation(lat, lng, city, mode)
    }
    suspend fun setAdhanEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ADHAN_ENABLED] = enabled }
    }
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    /** Called after a successful prayer time sync to record when and where sync occurred. */
    suspend fun recordPrayerSync(lat: Double, lng: Double) {
        val month = java.time.YearMonth.now().toString() // e.g. "2025-06"
        context.dataStore.edit {
            it[KEY_LAST_PRAYER_SYNC_MS] = System.currentTimeMillis()
            it[KEY_LAST_SYNC_LAT] = lat
            it[KEY_LAST_SYNC_LNG] = lng
            it[KEY_LAST_SYNC_MONTH] = month
        }
    }

    /** Returns true if we should sync prayer times based on time, location or month change. */
    suspend fun shouldSyncPrayerTimes(currentLat: Double, currentLng: Double): Boolean {
        val prefs = context.dataStore.data.first()
        val lastSyncMs = prefs[KEY_LAST_PRAYER_SYNC_MS] ?: 0L
        val lastLat = prefs[KEY_LAST_SYNC_LAT] ?: 0.0
        val lastLng = prefs[KEY_LAST_SYNC_LNG] ?: 0.0
        val lastMonth = prefs[KEY_LAST_SYNC_MONTH] ?: ""
        val currentMonth = java.time.YearMonth.now().toString()

        // Condition 1: More than 6 hours since last sync
        val sixHoursMs = 6 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - lastSyncMs > sixHoursMs) return true

        // Condition 2: Month changed (need next month's prayer times)
        if (currentMonth != lastMonth) return true

        // Condition 3: User moved more than 5km
        if (currentLat != 0.0 && currentLng != 0.0 && lastLat != 0.0 && lastLng != 0.0) {
            val distanceKm = haversineDistanceKm(currentLat, currentLng, lastLat, lastLng)
            if (distanceKm > 5.0) return true
        }

        return false
    }

    private fun haversineDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    // --- Per-Prayer Lock Enable/Disable ---
    val lockFajr: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOCK_FAJR] ?: true }
    val lockDhuhr: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOCK_DHUHR] ?: true }
    val lockAsr: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOCK_ASR] ?: true }
    val lockMaghrib: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOCK_MAGHRIB] ?: true }
    val lockIsha: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOCK_ISHA] ?: true }

    fun isPrayerLockEnabled(prayer: com.salahlock.app.data.model.PrayerName): Flow<Boolean> = when (prayer) {
        com.salahlock.app.data.model.PrayerName.FAJR -> lockFajr
        com.salahlock.app.data.model.PrayerName.DHUHR -> lockDhuhr
        com.salahlock.app.data.model.PrayerName.ASR -> lockAsr
        com.salahlock.app.data.model.PrayerName.MAGHRIB -> lockMaghrib
        com.salahlock.app.data.model.PrayerName.ISHA -> lockIsha
        else -> kotlinx.coroutines.flow.flowOf(false)
    }

    suspend fun setPrayerLockEnabled(prayer: com.salahlock.app.data.model.PrayerName, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val key = when (prayer) {
                com.salahlock.app.data.model.PrayerName.FAJR -> KEY_LOCK_FAJR
                com.salahlock.app.data.model.PrayerName.DHUHR -> KEY_LOCK_DHUHR
                com.salahlock.app.data.model.PrayerName.ASR -> KEY_LOCK_ASR
                com.salahlock.app.data.model.PrayerName.MAGHRIB -> KEY_LOCK_MAGHRIB
                com.salahlock.app.data.model.PrayerName.ISHA -> KEY_LOCK_ISHA
                else -> return@edit
            }
            prefs[key] = enabled
        }
    }

    // --- Block Profile ---
    val blockProfile: Flow<String> = context.dataStore.data.map { it[KEY_BLOCK_PROFILE] ?: "CUSTOM" }

    suspend fun setBlockProfile(profile: String) {
        context.dataStore.edit { it[KEY_BLOCK_PROFILE] = profile }
    }

    // --- Sprint E.2: Local Backup ---
    val lastBackupMs: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_BACKUP_MS] ?: 0L }
    val autoBackupFrequency: Flow<String> = context.dataStore.data.map { it[KEY_AUTO_BACKUP_FREQ] ?: "DISABLED" }

    suspend fun setLastBackupMs(ms: Long) {
        context.dataStore.edit { it[KEY_LAST_BACKUP_MS] = ms }
    }
    suspend fun setAutoBackupFrequency(freq: String) {
        context.dataStore.edit { it[KEY_AUTO_BACKUP_FREQ] = freq }
    }

    // --- Sprint E.1: Prayer Source ---
    val prayerSource: Flow<String> = context.dataStore.data
        .map { it[KEY_PRAYER_SOURCE] ?: "API" }

    suspend fun setPrayerSource(source: String) {
        context.dataStore.edit { it[KEY_PRAYER_SOURCE] = source }
    }

    // --- Sprint E: Verification Preferences ---
    val verificationMethod: Flow<String> = context.dataStore.data
        .map { it[KEY_VERIFICATION_METHOD] ?: "ASK_EVERY_TIME" }

    val verificationConfirmCount: Flow<Int> = context.dataStore.data
        .map { it[KEY_VERIFICATION_CONFIRM_COUNT] ?: 3 }

    val reminderQuranEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_REMINDER_QURAN] ?: true }

    val reminderHadithEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_REMINDER_HADITH] ?: true }

    val reminderReflectionEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_REMINDER_REFLECTION] ?: true }

    suspend fun setVerificationMethod(method: String) {
        context.dataStore.edit { it[KEY_VERIFICATION_METHOD] = method }
    }

    suspend fun setVerificationConfirmCount(count: Int) {
        context.dataStore.edit { it[KEY_VERIFICATION_CONFIRM_COUNT] = count.coerceIn(1, 3) }
    }

    suspend fun setReminderQuranEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REMINDER_QURAN] = enabled }
    }

    suspend fun setReminderHadithEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REMINDER_HADITH] = enabled }
    }

    suspend fun setReminderReflectionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REMINDER_REFLECTION] = enabled }
    }

    // --- Hadith Reading Position Persistence ---
    fun getHadithPosition(key: String): Flow<Int> {
        val prefKey = intPreferencesKey("hadith_pos_$key")
        return context.dataStore.data.map { it[prefKey] ?: 0 }
    }

    suspend fun saveHadithPosition(key: String, index: Int) {
        val prefKey = intPreferencesKey("hadith_pos_$key")
        context.dataStore.edit { it[prefKey] = index }
    }
}
