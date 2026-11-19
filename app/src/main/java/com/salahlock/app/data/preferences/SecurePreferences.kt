package com.salahlock.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.security.GeneralSecurityException

class SecurePreferences(
    context: Context,
    private val preferencesFileName: String = SECURE_PREFS_FILE,
    private val masterKeyAlias: String = MasterKey.DEFAULT_MASTER_KEY_ALIAS,
) {

    private val sharedPreferences: SharedPreferences = openEncryptedPreferences(context)

    companion object {
        private const val TAG = "SecurePreferences"
        private const val SECURE_PREFS_FILE = "secure_user_prefs"
        private const val KEY_USER_LAT = "user_lat"
        private const val KEY_USER_LNG = "user_user_lng"
        private const val KEY_CITY_NAME = "city_name"
        private const val KEY_LOCATION_MODE = "location_mode"
    }

    /**
     * EncryptedSharedPreferences files are device-bound. If Android restores or a
     * user copies the file without its Keystore key, Tink throws AEADBadTagException
     * during Application startup. Recover by deleting only the unreadable secure
     * location preferences; Room and regular DataStore data remain untouched.
     */
    private fun openEncryptedPreferences(context: Context): SharedPreferences {
        return try {
            createEncryptedPreferences(context)
        } catch (error: GeneralSecurityException) {
            recoverUnreadablePreferences(context, error)
        } catch (error: IOException) {
            recoverUnreadablePreferences(context, error)
        }
    }

    private fun recoverUnreadablePreferences(context: Context, error: Exception): SharedPreferences {
        Log.e(TAG, "Secure preferences could not be decrypted; resetting device-bound values", error)
        if (!context.deleteSharedPreferences(preferencesFileName)) {
            context.getSharedPreferences(preferencesFileName, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
        return createEncryptedPreferences(context)
    }

    private fun createEncryptedPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context, masterKeyAlias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            preferencesFileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
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
