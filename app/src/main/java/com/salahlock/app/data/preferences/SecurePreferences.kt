package com.salahlock.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_user_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_USER_LAT = "user_lat"
        private const val KEY_USER_LNG = "user_user_lng"
        private const val KEY_CITY_NAME = "city_name"
        private const val KEY_LOCATION_MODE = "location_mode"
    }

    private val _userLat = MutableStateFlow(sharedPreferences.getFloat(KEY_USER_LAT, 0f).toDouble())
    val userLat: StateFlow<Double> = _userLat.asStateFlow()

    private val _userLng = MutableStateFlow(sharedPreferences.getFloat(KEY_USER_LNG, 0f).toDouble())
    val userLng: StateFlow<Double> = _userLng.asStateFlow()

    private val _cityName = MutableStateFlow(sharedPreferences.getString(KEY_CITY_NAME, "") ?: "")
    val cityName: StateFlow<String> = _cityName.asStateFlow()

    private val _locationMode = MutableStateFlow(sharedPreferences.getString(KEY_LOCATION_MODE, "GPS") ?: "GPS")
    val locationMode: StateFlow<String> = _locationMode.asStateFlow()

    fun setLocation(lat: Double, lng: Double, city: String, mode: String) {
        sharedPreferences.edit()
            .putFloat(KEY_USER_LAT, lat.toFloat())
            .putFloat(KEY_USER_LNG, lng.toFloat())
            .putString(KEY_CITY_NAME, city)
            .putString(KEY_LOCATION_MODE, mode)
            .apply()

        _userLat.value = lat
        _userLng.value = lng
        _cityName.value = city
        _locationMode.value = mode
    }
}
