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

/** Размер приложения по данным системы (StorageStatsManager). */
data class AppSize(val appBytes: Long, val dataBytes: Long, val cacheBytes: Long) {
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}

data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val installedAtMs: Long,
    /** null — система не знает о запусках за период наблюдения. */
    val lastUsedMs: Long?,
    val foregroundMs7d: Long,
    /** Есть ли у пакета значок запуска. У служебных системных пакетов (сервисы, WebView) его нет. */
    val hasLauncher: Boolean = true,
    val isEnabled: Boolean = true,
    /** null — размер неизвестен (нет доступа к статистике или ещё считается). */
    val size: AppSize? = null
)

/** Сколько полных суток приложение не использовалось (если запусков нет — со дня установки). */
fun AppEntry.idleDays(nowMs: Long): Long {
    val reference = lastUsedMs ?: installedAtMs
    return (nowMs - reference).coerceAtLeast(0L) / DAY_MS
}

/** «Давно не используется» осмысленно только для включённых пользовательских приложений со значком запуска. */
fun AppEntry.isIdle(nowMs: Long, thresholdDays: Int = IDLE_THRESHOLD_DAYS): Boolean =
    !isSystem && hasLauncher && isEnabled && idleDays(nowMs) >= thresholdDays

enum class AppSort { IDLE, SCREEN_TIME, SIZE, NAME }

enum class AppScope { USER, SYSTEM, ALL }

fun filterApps(apps: List<AppEntry>, query: String, scope: AppScope): List<AppEntry> {
    val q = query.trim()
    return apps.filter { app ->
        val inScope = when (scope) {
            AppScope.USER -> !app.isSystem
            AppScope.SYSTEM -> app.isSystem
            AppScope.ALL -> true
        }
        inScope &&
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
    AppSort.SIZE -> sortedWith(
        compareByDescending<AppEntry> { it.size?.totalBytes ?: -1L }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
    )
    AppSort.NAME -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}

/** Самые крупные приложения; по умолчанию без системных — их всё равно не удалить. */
fun topBySize(apps: List<AppEntry>, count: Int, includeSystem: Boolean = false): List<AppEntry> =
    apps.filter { it.size != null && (includeSystem || !it.isSystem) }
        .sortedByDescending { it.size?.totalBytes ?: 0L }
        .take(count)

/** Из чего состоит занятое место. Части не пересекаются, сумма равна общему объёму. */
data class StorageBreakdown(
    val appsBytes: Long,
    val cacheBytes: Long,
    val otherBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long
)

/**
 * Приложения и кэш — по данным StorageStatsManager, «прочее» — остаток занятого места
 * (система, фото, загрузки). Если приложения «весят» больше занятого (общие файлы), значения ужимаются.
 */
fun storageBreakdown(snapshot: DeviceSnapshot, apps: List<AppEntry>): StorageBreakdown? {
    val sizes = apps.mapNotNull { it.size }
    val total = snapshot.totalStorageBytes
    if (sizes.isEmpty() || total <= 0L) return null
    val free = snapshot.freeStorageBytes.coerceIn(0L, total)
    val used = total - free
    val cache = sizes.sumOf { it.cacheBytes }.coerceAtMost(used)
    val appsBytes = sizes.sumOf { it.appBytes + it.dataBytes }.coerceAtMost(used - cache)
    return StorageBreakdown(appsBytes, cache, used - cache - appsBytes, free, total)
}

/** Разбор длительности на часы и минуты. */
fun durationParts(ms: Long): Pair<Long, Long> {
    val totalMinutes = ms.coerceAtLeast(0L) / 60_000L
    return totalMinutes / 60L to totalMinutes % 60L
}
