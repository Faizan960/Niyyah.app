package com.salahlock.app.camera

import android.graphics.Bitmap
import com.salahlock.app.data.model.VerificationResult

/**
 * Extension point for prayer verification strategies.
 *
 * Current implementation: [RuleBasedVerificationProvider] (brightness + tilt + edge density).
 * Future implementation: [MLVerificationProvider] (TFLite prayer mat detection — not yet trained).
 *
 * The [CameraVerificationActivity] uses [VerificationProviderFactory.getActiveProvider]
 * so the verification strategy can be swapped without touching the camera UI.
 */
interface VerificationProvider {
    /** Stable identifier for logging and settings persistence. */
    val providerId: String

    /** Human-readable name shown in the lock session details card. */
    val displayName: String

    /**
     * Synchronous verification. Called on a background thread from the CameraX executor.
     * Must return quickly (< 2 seconds per CLAUDE.md §10 ML performance requirements).
     */
    fun verify(bitmap: Bitmap, gravityValues: FloatArray?): VerificationResult

    /** User-facing message explaining a failed verification result. */
    fun getFeedbackMessage(result: VerificationResult): String
}
