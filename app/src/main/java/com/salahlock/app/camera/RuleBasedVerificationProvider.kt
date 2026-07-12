package com.salahlock.app.camera

import android.graphics.Bitmap
import com.salahlock.app.data.model.VerificationResult

/**
 * Rule-based verification — the current production implementation.
 *
 * Checks three signals in order:
 *  1. Brightness — rejects images that are too dark to see anything meaningful.
 *  2. Tilt angle — phone must be pointed downward (toward the prayer mat).
 *  3. Edge/texture density — the surface must have enough visual detail to
 *     distinguish a prayer mat from a plain wall or blank surface.
 *
 * This is NOT a prayer mat detector. It creates behavioral friction —
 * the user must be in a physical position consistent with praying.
 * A future [MLVerificationProvider] will provide true mat classification.
 */
class RuleBasedVerificationProvider : VerificationProvider {

    override val providerId: String = "rule_based"
    override val displayName: String = "Camera Verification"

    override fun verify(bitmap: Bitmap, gravityValues: FloatArray?): VerificationResult {
        return ImageVerifier.verify(bitmap, gravityValues)
    }

    override fun getFeedbackMessage(result: VerificationResult): String {
        return ImageVerifier.getFeedbackMessage(result)
    }
}
