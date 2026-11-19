package com.salahlock.app.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class SecurePreferencesRecoveryTest {
    private val prefsFile = "secure_prefs_recovery_test"
    private val keyAlias = "secure_prefs_recovery_test_key"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cleanup()
    }

    @After
    fun tearDown() = cleanup()

    @Test
    fun missingKeystoreKey_resetsOnlyUnreadableEncryptedPreferences() = runBlocking {
        SecurePreferences(context, prefsFile, keyAlias)
            .setLocation(19.076, 72.8777, "Mumbai", "GPS")

        androidKeyStore().deleteEntry(keyAlias)

        val recovered = SecurePreferences(context, prefsFile, keyAlias)
        assertEquals(0.0, recovered.userLat.first(), 0.0)
        assertEquals(0.0, recovered.userLng.first(), 0.0)
        assertEquals("", recovered.cityName.first())

        recovered.setLocation(17.385, 78.4867, "Hyderabad", "GPS")
        assertEquals("Hyderabad", recovered.cityName.first())
    }

    private fun cleanup() {
        if (::context.isInitialized) context.deleteSharedPreferences(prefsFile)
        androidKeyStore().deleteEntry(keyAlias)
    }

    private fun androidKeyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
}
