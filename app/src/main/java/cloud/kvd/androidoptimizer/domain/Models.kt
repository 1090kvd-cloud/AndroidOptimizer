package cloud.kvd.androidoptimizer.domain

/** Уровень важности: используется и для цвета метрик, и для рекомендаций. */
enum class Level { NORMAL, INFO, WARNING, CRITICAL }

data class BatteryState(
    val percent: Int?,
    val isCharging: Boolean,
    val temperatureC: Float?
)

data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val isLowMemory: Boolean,
    val totalStorageBytes: Long,
    val freeStorageBytes: Long,
    val battery: BatteryState
) {
    val ramUsedFraction: Float get() = usedFraction(totalRamBytes, availableRamBytes)
    val storageUsedFraction: Float get() = usedFraction(totalStorageBytes, freeStorageBytes)

    val displayName: String
        get() = if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            manufacturer.replaceFirstChar { it.uppercase() } + " " + model
        }
}

/** Доля занятого объёма [0..1]. Некорректные входные данные не приводят к выходу за границы. */
fun usedFraction(total: Long, free: Long): Float {
    if (total <= 0L) return 0f
    val safeFree = free.coerceIn(0L, total)
    return ((total - safeFree).toDouble() / total.toDouble()).toFloat()
}

/** Доля свободного объёма [0..1] в двойной точности (для порогов). */
fun freeFraction(total: Long, free: Long): Double {
    if (total <= 0L) return 1.0
    return free.coerceIn(0L, total).toDouble() / total.toDouble()
}

const val DAY_MS = 86_400_000L

/** Приложение без запусков дольше этого срока считается неиспользуемым. Синхронизировано со строками reco_idle_*. */
const val IDLE_THRESHOLD_DAYS = 60

data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val installedAtMs: Long,
    /** null — система не знает о запусках за период наблюдения. */
    val lastUsedMs: Long?,
    val foregroundMs7d: Long
)

/** Сколько полных суток приложение не использовалось (если запусков нет — со дня установки). */
fun AppEntry.idleDays(nowMs: Long): Long {
    val reference = lastUsedMs ?: installedAtMs
    return (nowMs - reference).coerceAtLeast(0L) / DAY_MS
}

fun AppEntry.isIdle(nowMs: Long, thresholdDays: Int = IDLE_THRESHOLD_DAYS): Boolean =
    !isSystem && idleDays(nowMs) >= thresholdDays

enum class AppSort { IDLE, SCREEN_TIME, NAME }

fun filterApps(apps: List<AppEntry>, query: String, includeSystem: Boolean): List<AppEntry> {
    val q = query.trim()
    return apps.filter { app ->
        (includeSystem || !app.isSystem) &&
            (q.isEmpty() || app.label.contains(q, ignoreCase = true) || app.packageName.contains(q, ignoreCase = true))
    }
}

fun List<AppEntry>.sortedFor(sort: AppSort): List<AppEntry> = when (sort) {
    AppSort.IDLE -> sortedWith(
        compareBy<AppEntry> { it.lastUsedMs ?: it.installedAtMs }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
    )
    AppSort.SCREEN_TIME -> sortedWith(
        compareByDescending<AppEntry> { it.foregroundMs7d }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
    )
    AppSort.NAME -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}

/** Разбор длительности на часы и минуты. */
fun durationParts(ms: Long): Pair<Long, Long> {
    val totalMinutes = ms.coerceAtLeast(0L) / 60_000L
    return totalMinutes / 60L to totalMinutes % 60L
}
