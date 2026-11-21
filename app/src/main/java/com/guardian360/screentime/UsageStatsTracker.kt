package com.guardian360.screentime

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.guardian360.common.safeStartActivity
import com.guardian360.database.CachedAppInventoryEntity
import com.guardian360.policies.AppCategoryCatalog
import java.util.Calendar

data class DeviceTelemetrySnapshot(
    val batteryPercent: Int,
    val isOnline: Boolean,
    val hasUsageAccess: Boolean,
    val totalMinutesToday: Long,
    val totalMinutesWeek: Long,
    val apps: List<CachedAppInventoryEntity>
)

class UsageStatsTracker(private val context: Context) {

    fun hasUsageStatsPermission(): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun buildUsageAccessIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }

    fun openUsageAccessSettings(hostContext: Context = context): Result<Unit> {
        val candidates = listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.fromParts("package", hostContext.packageName, null)
            },
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", hostContext.packageName, null)
            },
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            val res = hostContext.safeStartActivity(intent)
            if (res.isSuccess) return res
        }
        return Result.failure(IllegalStateException("Unable to open Usage Access settings on this device."))
    }

    fun openAppDetailsSettings(hostContext: Context = context): Result<Unit> {
        val candidates = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", hostContext.packageName, null)
            },
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            val res = hostContext.safeStartActivity(intent)
            if (res.isSuccess) return res
        }
        return Result.failure(IllegalStateException("Unable to open App Settings on this device."))
    }

    fun getBatteryLevel(): Int = runCatching {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return -1
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(-1, 100)
    }.getOrDefault(-1)

    fun isDeviceOnline(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    fun collectSnapshot(blockedPackages: Set<String> = emptySet()): DeviceTelemetrySnapshot {
        val hasUsage = hasUsageStatsPermission()
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchableApps = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, 0)
            }
        }.getOrDefault(emptyList())

        val todayMap = mutableMapOf<String, Long>()
        val weekMap = mutableMapOf<String, Long>()

        if (hasUsage) {
            runCatching {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                if (usm != null) {
                    val now = System.currentTimeMillis()
                    val startOfDay = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    val dailyStats = runCatching { usm.queryAndAggregateUsageStats(startOfDay, now) }.getOrNull().orEmpty()
                    dailyStats.forEach { (pkg, stat) ->
                        val mins = stat.totalTimeInForeground / 60_000L
                        if (mins > 0) todayMap[pkg] = mins
                    }

                    val weekAgo = now - 7L * 24L * 3600_000L
                    val weeklyStats = runCatching { usm.queryAndAggregateUsageStats(weekAgo, now) }.getOrNull().orEmpty()
                    weeklyStats.forEach { (pkg, stat) ->
                        val mins = stat.totalTimeInForeground / 60_000L
                        if (mins > 0) weekMap[pkg] = mins
                    }
                }
            }
        }

        val nowMs = System.currentTimeMillis()
        val seen = HashSet<String>()
        val inventory = launchableApps.mapNotNull { resolveInfo ->
            val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
            val pkg = appInfo.packageName ?: return@mapNotNull null
            if (!seen.add(pkg)) return@mapNotNull null
            val label = runCatching { resolveInfo.loadLabel(pm)?.toString() }.getOrNull()?.ifBlank { pkg } ?: pkg
            val (category, gameSub, ageRating) = AppCategoryCatalog.classify(appInfo)
            val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            CachedAppInventoryEntity(
                packageName = pkg,
                appLabel = label,
                category = category.name,
                gameSubcategory = gameSub?.name,
                ageRating = ageRating.name,
                isSystemApp = isSys,
                isBlocked = pkg in blockedPackages,
                minutesToday = todayMap[pkg] ?: 0L,
                minutesWeek = weekMap[pkg] ?: 0L,
                updatedAtEpochMs = nowMs
            )
        }.sortedWith(compareByDescending<CachedAppInventoryEntity> { it.minutesToday }.thenBy { it.appLabel })

        return DeviceTelemetrySnapshot(
            batteryPercent = getBatteryLevel(),
            isOnline = isDeviceOnline(),
            hasUsageAccess = hasUsage,
            totalMinutesToday = inventory.sumOf { it.minutesToday },
            totalMinutesWeek = inventory.sumOf { it.minutesWeek },
            apps = inventory
        )
    }
}
