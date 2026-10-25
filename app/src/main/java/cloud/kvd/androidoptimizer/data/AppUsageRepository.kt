package cloud.kvd.androidoptimizer.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import cloud.kvd.androidoptimizer.domain.AppEntry
import cloud.kvd.androidoptimizer.domain.AppSize
import cloud.kvd.androidoptimizer.domain.DAY_MS
import java.io.IOException

/**
 * Полный список установленных пакетов + статистика использования и размеры.
 * Для полного списка нужно QUERY_ALL_PACKAGES (приложение не предназначено для Google Play).
 * Все методы блокирующие — вызывать вне главного потока.
 */
class AppUsageRepository(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = checkUsageOp(appOps)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun loadApps(nowMs: Long): List<AppEntry> {
        val pm = context.packageManager
        val usage = if (hasUsageAccess()) readUsage(nowMs) else UsageData.EMPTY
        val launchable = queryLaunchables(pm).map { it.activityInfo.packageName }.toHashSet()

        return queryInstalled(pm)
            .mapNotNull { pkg ->
                val info = pkg.applicationInfo ?: return@mapNotNull null
                if (info.packageName == context.packageName) return@mapNotNull null
                AppEntry(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    installedAtMs = pkg.firstInstallTime.takeIf { it > 0L } ?: nowMs,
                    lastUsedMs = usage.lastUsed[info.packageName],
                    foregroundMs7d = usage.foreground7d[info.packageName] ?: 0L,
                    hasLauncher = info.packageName in launchable,
                    isEnabled = info.enabled
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Размеры приложений (код + данные + кэш). Требует Usage Access; пакеты, для которых система
     * не отдала данные, пропускаются. [shouldContinue] позволяет прервать долгий обход.
     */
    fun loadSizes(packageNames: List<String>, shouldContinue: () -> Boolean = { true }): Map<String, AppSize> {
        val manager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val user = Process.myUserHandle()
        val result = HashMap<String, AppSize>(packageNames.size)
        for (name in packageNames) {
            if (!shouldContinue()) break
            try {
                val stats = manager.queryStatsForPackage(StorageManager.UUID_DEFAULT, name, user)
                result[name] = AppSize(stats.appBytes, stats.dataBytes, stats.cacheBytes)
            } catch (e: PackageManager.NameNotFoundException) {
                // пакет исчез между запросами
            } catch (e: IOException) {
                // система не смогла посчитать — пропускаем
            } catch (e: SecurityException) {
                return result // доступа нет вовсе
            }
        }
        return result
    }

    private class UsageData(val lastUsed: Map<String, Long>, val foreground7d: Map<String, Long>) {
        companion object {
            val EMPTY = UsageData(emptyMap(), emptyMap())
        }
    }

    private fun readUsage(nowMs: Long): UsageData = try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        // Месячные корзины хранятся около 6 месяцев; данные приблизительные.
        val long = usm.queryAndAggregateUsageStats(nowMs - LOOKBACK_DAYS * DAY_MS, nowMs)
        val week = usm.queryAndAggregateUsageStats(nowMs - 7 * DAY_MS, nowMs)
        UsageData(
            lastUsed = long.mapValues { it.value.lastTimeUsed }.filterValues { it > 0L },
            foreground7d = week.mapValues { it.value.totalTimeInForeground }
        )
    } catch (e: SecurityException) {
        UsageData.EMPTY
    }

    @Suppress("DEPRECATION")
    private fun checkUsageOp(appOps: AppOpsManager): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        }

    @Suppress("DEPRECATION")
    private fun queryLaunchables(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun queryInstalled(pm: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            pm.getInstalledPackages(0)
        }

    private companion object {
        const val LOOKBACK_DAYS = 180L
    }
}
