package com.salahlock.app.verification

enum class VerificationMethod {
    TEXT,
    VOICE,
    ASK_EVERY_TIME,

    /**
     * Production default — camera UI is not shown.
     * Routes to ASK_EVERY_TIME behaviour at runtime.
     */
    CAMERA_DISABLED,

    /**
     * Future ML path — reserved for Sprint ML.3+.
     * Routes to VerificationProviderFactory → MLVerificationProvider → TFLite.
     * Not reachable from any UI in v1.
     */
    CAMERA_AI_FUTURE;

    companion object {
        fun fromString(value: String): VerificationMethod =
            entries.firstOrNull { it.name == value } ?: ASK_EVERY_TIME

        /** Migration: any legacy CAMERA value → TEXT, then prompt user to re-select. */
        fun VerificationMethod.migrated(): VerificationMethod = when (this) {
            CAMERA_DISABLED, CAMERA_AI_FUTURE -> ASK_EVERY_TIME
            else -> this
        }

        /** Returns the effective runtime method — resolves system-level values to user-facing ones. */
        fun VerificationMethod.effective(): VerificationMethod = when (this) {
            CAMERA_DISABLED, CAMERA_AI_FUTURE -> ASK_EVERY_TIME
            else -> this
        }
    }
}
