package cloud.kvd.androidoptimizer.domain

enum class RecommendationType { STORAGE_LOW, RAM_LOW, BATTERY_HOT, BATTERY_LOW, USAGE_ACCESS, UNUSED_APPS }

data class Recommendation(
    val type: RecommendationType,
    val level: Level,
    val count: Int = 0
)

fun storageLevel(s: DeviceSnapshot): Level {
    if (s.totalStorageBytes <= 0L) return Level.NORMAL
    val free = freeFraction(s.totalStorageBytes, s.freeStorageBytes)
    return when {
        free < 0.10 -> Level.CRITICAL
        free < 0.20 -> Level.WARNING
        else -> Level.NORMAL
    }
}

/**
 * Android намеренно держит кэш приложений в ОЗУ, поэтому малый объём свободной памяти — норма.
 * Предупреждаем только при флаге lowMemory от системы или при очень малом остатке.
 */
fun ramLevel(s: DeviceSnapshot): Level {
    val veryLow = s.totalRamBytes > 0L && freeFraction(s.totalRamBytes, s.availableRamBytes) < 0.08
    return if (s.isLowMemory || veryLow) Level.WARNING else Level.NORMAL
}

fun batteryTempLevel(b: BatteryState): Level {
    val t = b.temperatureC ?: return Level.NORMAL
    return when {
        t >= 45f -> Level.CRITICAL
        t >= 40f -> Level.WARNING
        else -> Level.NORMAL
    }
}

fun batteryChargeLevel(b: BatteryState): Level {
    val p = b.percent ?: return Level.NORMAL
    if (b.isCharging) return Level.NORMAL
    return when {
        p <= 5 -> Level.CRITICAL
        p <= 15 -> Level.WARNING
        else -> Level.NORMAL
    }
}

fun buildRecommendations(
    snapshot: DeviceSnapshot,
    usageAccessGranted: Boolean,
    idleAppsCount: Int
): List<Recommendation> {
    val result = mutableListOf<Recommendation>()

    storageLevel(snapshot).takeIf { it != Level.NORMAL }
        ?.let { result += Recommendation(RecommendationType.STORAGE_LOW, it) }
    ramLevel(snapshot).takeIf { it != Level.NORMAL }
        ?.let { result += Recommendation(RecommendationType.RAM_LOW, it) }
    batteryTempLevel(snapshot.battery).takeIf { it != Level.NORMAL }
        ?.let { result += Recommendation(RecommendationType.BATTERY_HOT, it) }
    batteryChargeLevel(snapshot.battery).takeIf { it != Level.NORMAL }
        ?.let { result += Recommendation(RecommendationType.BATTERY_LOW, it) }

    if (!usageAccessGranted) {
        result += Recommendation(RecommendationType.USAGE_ACCESS, Level.INFO)
    } else if (idleAppsCount > 0) {
        result += Recommendation(RecommendationType.UNUSED_APPS, Level.INFO, idleAppsCount)
    }

    // sortedByDescending стабилен: внутри одного уровня порядок правил сохраняется.
    return result.sortedByDescending { it.level.ordinal }
}
