package com.salahlock.app.ui.qibla

import android.app.Application
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Qibla
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.model.PrayerTime
import com.salahlock.app.util.CompassHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.*

data class QiblaUiState(
    val azimuth: Float = 0f,
    val qiblaAngle: Float = 0f,
    val distanceKm: Int = 0,
    val accuracy: String = "High",
    val cityName: String = "",
    val locationMissing: Boolean = false,
    val nextPrayer: PrayerTime? = null,
    val currentTimeMs: Long = System.currentTimeMillis()
)

class QiblaViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication
    private val compassHelper = CompassHelper(application)

    private val _uiState = MutableStateFlow(QiblaUiState())
    val uiState: StateFlow<QiblaUiState> = _uiState.asStateFlow()

    /** Prevents continuous haptic on every frame when aligned — fires only on alignment entry. */
    private var wasAligned = false

    init {
        observeCompass()
        observeLocationAndCalculateQibla()
        observeNextPrayer()
        tickClock()
    }

    fun startCompass() {
        compassHelper.start()
    }

    fun stopCompass() {
        compassHelper.stop()
    }

    private fun observeCompass() {
        viewModelScope.launch {
            compassHelper.azimuth.collect { azimuth ->
                _uiState.update { it.copy(azimuth = azimuth) }

                // Check Qibla alignment ±5° — haptic fires once on entry, stops on exit
                val qiblaAngle = _uiState.value.qiblaAngle
                val angleDiff = kotlin.math.abs(
                    ((azimuth - qiblaAngle + 540) % 360) - 180
                )
                val isAligned = angleDiff <= 5f
                if (isAligned && !wasAligned) {
                    wasAligned = true
                    vibrateAligned()
                } else if (!isAligned) {
                    wasAligned = false
                }
            }
        }
        viewModelScope.launch {
            compassHelper.accuracy.collect { acc ->
                _uiState.update { it.copy(accuracy = acc) }
            }
        }
    }

    private fun observeLocationAndCalculateQibla() {
        viewModelScope.launch {
            combine(
                app.userPreferences.userLat,
                app.userPreferences.userLng,
                app.userPreferences.cityName
            ) { lat, lng, city ->
                Triple(lat, lng, city)
            }.collect { (lat, lng, city) ->
                if (lat == 0.0 && lng == 0.0) {
                    _uiState.update { it.copy(locationMissing = true, cityName = "") }
                } else {
                    val qibla = Qibla(Coordinates(lat, lng)).direction.toFloat()
                    val distance = calculateDistanceToMakkah(lat, lng)
                    _uiState.update { 
                        it.copy(
                            qiblaAngle = qibla,
                            distanceKm = distance,
                            locationMissing = false,
                            cityName = city
                        ) 
                    }
                }
            }
        }
    }

    private fun observeNextPrayer() {
        viewModelScope.launch {
            app.prayerTimesRepository.getTodayPrayers().collect { daily ->
                if (daily != null) {
                    val now = LocalDateTime.now()
                    val next = daily.nextPrayer(now) ?: daily.toFullList().firstOrNull()
                    _uiState.update { it.copy(nextPrayer = next) }
                }
            }
        }
    }

    private fun tickClock() {
        viewModelScope.launch {
            while (true) {
                // SL-021: park the tick while nothing collects uiState (QiblaScreen
                // uses collectAsStateWithLifecycle) — no background clock work.
                _uiState.subscriptionCount.first { it > 0 }
                kotlinx.coroutines.delay(1000)
                _uiState.update { it.copy(currentTimeMs = System.currentTimeMillis()) }
            }
        }
    }

    private fun calculateDistanceToMakkah(lat: Double, lng: Double): Int {
        val makkahLat = 21.422487
        val makkahLng = 39.826206
        val R = 6371.0
        val dLat = Math.toRadians(makkahLat - lat)
        val dLon = Math.toRadians(makkahLng - lng)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat)) * cos(Math.toRadians(makkahLat)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (R * c).roundToInt()
    }

    /** Short haptic pulse when Qibla alignment is achieved. Fires once per alignment event. */
    @Suppress("DEPRECATION")
    private fun vibrateAligned() {
        val context = getApplication<SalahLockApplication>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(80)
        }
    }
}
