# ============================================================================
# SalahLock — R8 / ProGuard rules (release build)
# Created in RC.2 release-blocker sprint. minify + resource shrinking rely on
# these keep rules; without them serialization, Room, and Credential Manager
# break at runtime in release.
# ============================================================================

# ── Kotlin metadata / reflection ────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes InnerClasses,EnclosingMethod
-keepattributes Exceptions

# ── kotlinx.serialization ───────────────────────────────────────────────────
# Backup/restore (BackupPackage and all @Serializable models) depends on this.
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *; }
-dontwarn kotlinx.serialization.**

# Keep every @Serializable class plus its generated $$serializer.
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclasseswithmembers class ** {
    public static ** Companion;
}
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers,allowshrinking class * {
    *** *$serializer;
}
-keepclassmembers class **$$serializer { *; }

# App data models that are serialized to the backup ZIP / parsed from APIs.
-keep class com.salahlock.app.data.backup.** { *; }
-keep class com.salahlock.app.data.api.model.** { *; }
-keep class com.salahlock.app.data.db.entity.** { *; }

# ── Room ────────────────────────────────────────────────────────────────────
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# ── Retrofit / Gson / OkHttp ────────────────────────────────────────────────
# Aladhan + Hadith REST clients.
-keep class com.salahlock.app.data.api.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ── Credential Manager / Google Sign-In ─────────────────────────────────────
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# ── Jetpack Compose ─────────────────────────────────────────────────────────
# AGP ships Compose rules via consumer ProGuard files; these are safety nets.
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ── Adhan (prayer time calculation) ─────────────────────────────────────────
-keep class com.batoulapps.adhan.** { *; }

# ── WorkManager ─────────────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class androidx.work.impl.** { *; }

# ── Kotlin coroutines ───────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ── Enums (valueOf / values reflective use across the app) ───────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Parcelables ─────────────────────────────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ── Strip debug logging from release (RC.2 Phase 5) ─────────────────────────
# Removes Log.d/v/i/w call sites entirely in release; Log.e kept for crash
# diagnosis. No source changes required.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static boolean isLoggable(...);
}
