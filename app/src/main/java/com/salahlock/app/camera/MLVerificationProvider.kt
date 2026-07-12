package com.salahlock.app.camera

import android.graphics.Bitmap
import com.salahlock.app.data.model.VerificationResult

/**
 * Placeholder for the future TFLite-based prayer mat verification model.
 *
 * This provider is NOT active in any build. It is a forward-compatibility
 * extension point that shows where the ML model will be integrated.
 *
 * Integration checklist (when ML sprint begins):
 *  [ ] Train MobileNetV3-Small on Indian prayer mat dataset (2,000–5,000 images)
 *  [ ] Convert to TFLite with INT8 quantization (<5MB)
 *  [ ] Place .tflite asset in app/src/main/assets/prayer_mat_model.tflite
 *  [ ] Implement [verify] using TFLite Interpreter + GPU delegate fallback
 *  [ ] Minimum confidence threshold: 0.78 (configurable via Remote Config)
 *  [ ] Measure inference time on Snapdragon 680-class device (target < 2s)
 *  [ ] Measure false positive rate on hard negatives (carpets, rugs, towels)
 *  [ ] Register this provider in [VerificationProviderFactory]
 */
class MLVerificationProvider : VerificationProvider {

    override val providerId: String = "ml_tflite"
    override val displayName: String = "AI Prayer Mat Detection"

    override fun verify(bitmap: Bitmap, gravityValues: FloatArray?): VerificationResult {
        // Not yet implemented — ML model is not trained.
        // Returning an error so this provider is never accidentally used in production.
        return VerificationResult.Error("ML verification is not yet available in this build.")
    }

    override fun getFeedbackMessage(result: VerificationResult): String = when (result) {
        is VerificationResult.Error -> result.message
        else -> "ML verification feedback not available."
    }
}
