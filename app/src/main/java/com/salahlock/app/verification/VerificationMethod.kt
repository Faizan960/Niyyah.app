package com.salahlock.app.verification

enum class VerificationMethod {
    TEXT,
    VOICE,
    ASK_EVERY_TIME;

    companion object {
        /**
         * Safe deserialization of a persisted value. Any unrecognized string —
         * including the legacy `CAMERA_DISABLED` / `CAMERA_AI_FUTURE` values written
         * by older installs before the camera verification stack was removed — maps
         * to the supported [ASK_EVERY_TIME] default instead of crashing.
         */
        fun fromString(value: String): VerificationMethod =
            entries.firstOrNull { it.name == value } ?: ASK_EVERY_TIME
    }
}
