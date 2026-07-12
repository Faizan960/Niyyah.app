package com.salahlock.app.camera

/**
 * Factory that returns the active [VerificationProvider].
 *
 * Currently always returns [RuleBasedVerificationProvider].
 * When the ML model is ready, add a user-facing setting and select based on it here.
 *
 * All callers ([CameraVerificationActivity], tests) use this factory so the
 * verification strategy can be changed in one place.
 */
object VerificationProviderFactory {

    enum class Mode { RULE_BASED, ML }

    /** The currently active mode. Switch to ML here when the model is ready. */
    val activeMode: Mode = Mode.RULE_BASED

    fun getActiveProvider(): VerificationProvider = when (activeMode) {
        Mode.RULE_BASED -> RuleBasedVerificationProvider()
        Mode.ML -> MLVerificationProvider()
    }
}
