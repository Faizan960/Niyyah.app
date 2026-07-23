package com.salahlock.app.util

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object PermissionHelper {

    /** Check if app can draw overlay (needed for LockOverlayActivity display) */
    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /** Check if usage stats permission is granted */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    /** Check if exact alarms can be scheduled (Android 12+) */
    fun canScheduleExactAlarms(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else {
            true // Always available below Android 12
        }

    /** Check location permission (coarse is sufficient for prayer time calculation) */
    fun hasLocationPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /** Check fine location permission */
    fun hasFineLocationPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /** Check notification permission (Android 13+) */
    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /** Is battery optimization ignored for this app? */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Returns true if all critical permissions needed for lock functionality are granted */
    fun hasCriticalPermissions(context: Context): Boolean =
        canDrawOverlays(context) && hasUsageStatsPermission(context)

    /** Returns a human-readable list of missing permissions for diagnostics */
    fun getMissingPermissions(context: Context): List<String> = buildList {
        if (!canDrawOverlays(context)) add("Display Over Apps")
        if (!hasUsageStatsPermission(context)) add("App Usage Access")
        if (!canScheduleExactAlarms(context)) add("Exact Alarm Scheduling")
        if (!hasLocationPermission(context)) add("Location")
        if (!isBatteryOptimizationIgnored(context)) add("Battery Optimization Exemption")
    }

    fun allPermissionsGranted(context: Context): Boolean =
        hasCriticalPermissions(context) &&
                hasLocationPermission(context)

    /** Intent to navigate directly to this app's overlay permission page */
    fun overlaySettingsIntent(context: Context) =
        android.content.Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    /** Intent to navigate to battery optimization settings for this app */
    fun batteryOptimizationIntent(context: Context) =
        android.content.Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
}
