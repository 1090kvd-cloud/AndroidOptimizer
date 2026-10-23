package cloud.kvd.androidoptimizer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {

    private val gb = 1_073_741_824L

    private fun snapshot(
        totalStorage: Long = 100 * gb,
        freeStorage: Long = 50 * gb,
        totalRam: Long = 8 * gb,
        freeRam: Long = 3 * gb,
        lowMemory: Boolean = false,
        battery: BatteryState = BatteryState(percent = 80, isCharging = false, temperatureC = 30f)
    ) = DeviceSnapshot(
        manufacturer = "samsung", model = "SM-S911B", androidVersion = "14", sdkInt = 34,
        totalRamBytes = totalRam, availableRamBytes = freeRam, isLowMemory = lowMemory,
        totalStorageBytes = totalStorage, freeStorageBytes = freeStorage, battery = battery
    )

    private fun app(
        pkg: String, label: String = pkg, system: Boolean = false,
        installed: Long = 0L, lastUsed: Long? = null, fg: Long = 0L
    ) = AppEntry(pkg, label, system, installed, lastUsed, fg)

    // ---- доли ----
    @Test fun usedFraction_basic() = assertEquals(0.75f, usedFraction(100, 25), 0.0001f)
    @Test fun usedFraction_zeroTotal() = assertEquals(0f, usedFraction(0, 10), 0f)
    @Test fun usedFraction_freeAboveTotalIsClamped() = assertEquals(0f, usedFraction(100, 500), 0f)
    @Test fun usedFraction_negativeFreeIsClamped() = assertEquals(1f, usedFraction(100, -5), 0f)

    // ---- уровни ----
    @Test fun storage_ok() = assertEquals(Level.NORMAL, storageLevel(snapshot(freeStorage = 50 * gb)))
    @Test fun storage_warning() = assertEquals(Level.WARNING, storageLevel(snapshot(freeStorage = 15 * gb)))
    @Test fun storage_critical() = assertEquals(Level.CRITICAL, storageLevel(snapshot(freeStorage = 5 * gb)))
    @Test fun storage_unknownTotalIsNormal() = assertEquals(Level.NORMAL, storageLevel(snapshot(totalStorage = 0, freeStorage = 0)))

    @Test fun ram_lowAvailableIsNotAlarmist() = assertEquals(Level.NORMAL, ramLevel(snapshot(freeRam = 1 * gb)))
    @Test fun ram_veryLowTriggers() = assertEquals(Level.WARNING, ramLevel(snapshot(totalRam = 100 * gb, freeRam = 5 * gb)))
    @Test fun ram_systemLowMemoryFlagTriggers() = assertEquals(Level.WARNING, ramLevel(snapshot(lowMemory = true)))

    @Test fun batteryTemp_levels() {
        assertEquals(Level.NORMAL, batteryTempLevel(BatteryState(50, false, 39.9f)))
        assertEquals(Level.WARNING, batteryTempLevel(BatteryState(50, false, 40f)))
        assertEquals(Level.CRITICAL, batteryTempLevel(BatteryState(50, false, 45f)))
        assertEquals(Level.NORMAL, batteryTempLevel(BatteryState(50, false, null)))
    }

    @Test fun batteryCharge_levels() {
        assertEquals(Level.NORMAL, batteryChargeLevel(BatteryState(16, false, null)))
        assertEquals(Level.WARNING, batteryChargeLevel(BatteryState(15, false, null)))
        assertEquals(Level.CRITICAL, batteryChargeLevel(BatteryState(5, false, null)))
        assertEquals(Level.NORMAL, batteryChargeLevel(BatteryState(3, true, null)))
        assertEquals(Level.NORMAL, batteryChargeLevel(BatteryState(null, false, null)))
    }

    // ---- рекомендации ----
    @Test fun recommendations_healthyDeviceWithAccessIsEmpty() {
        assertTrue(buildRecommendations(snapshot(), usageAccessGranted = true, idleAppsCount = 0).isEmpty())
    }

    @Test fun recommendations_withoutAccessAsksForAccessOnly() {
        val r = buildRecommendations(snapshot(), usageAccessGranted = false, idleAppsCount = 7)
        assertEquals(listOf(RecommendationType.USAGE_ACCESS), r.map { it.type })
    }

    @Test fun recommendations_idleAppsCountIsReported() {
        val r = buildRecommendations(snapshot(), usageAccessGranted = true, idleAppsCount = 4)
        assertEquals(1, r.size)
        assertEquals(RecommendationType.UNUSED_APPS, r[0].type)
        assertEquals(4, r[0].count)
    }

    @Test fun recommendations_sortedBySeverity() {
        val s = snapshot(freeStorage = 5 * gb, battery = BatteryState(10, false, 41f))
        val r = buildRecommendations(s, usageAccessGranted = true, idleAppsCount = 2)
        assertEquals(
            listOf(
                RecommendationType.STORAGE_LOW,   // CRITICAL
                RecommendationType.BATTERY_HOT,   // WARNING (порядок правил)
                RecommendationType.BATTERY_LOW,   // WARNING
                RecommendationType.UNUSED_APPS    // INFO
            ),
            r.map { it.type }
        )
        assertEquals(Level.CRITICAL, r[0].level)
    }

    // ---- приложения ----
    @Test fun idleDays_usesLastUsedThenInstallDate() {
        val now = 100 * DAY_MS
        assertEquals(10L, app("a", lastUsed = 90 * DAY_MS, installed = 0).idleDays(now))
        assertEquals(40L, app("b", lastUsed = null, installed = 60 * DAY_MS).idleDays(now))
        assertEquals(0L, app("c", lastUsed = 200 * DAY_MS).idleDays(now)) // время из «будущего» не уходит в минус
    }

    @Test fun isIdle_ignoresSystemApps() {
        val now = 500 * DAY_MS
        assertTrue(app("a", lastUsed = 1 * DAY_MS).isIdle(now))
        assertFalse(app("a", system = true, lastUsed = 1 * DAY_MS).isIdle(now))
        assertFalse(app("b", lastUsed = now - 59 * DAY_MS).isIdle(now))
        assertTrue(app("b", lastUsed = now - 60 * DAY_MS).isIdle(now))
    }

    @Test fun filter_hidesSystemAndMatchesQuery() {
        val apps = listOf(app("com.a", "Alpha"), app("com.b", "Beta", system = true), app("org.c", "Gamma"))
        assertEquals(listOf("com.a", "org.c"), filterApps(apps, "", false).map { it.packageName })
        assertEquals(3, filterApps(apps, "", true).size)
        assertEquals(listOf("com.a"), filterApps(apps, "  alp ", false).map { it.packageName })
        assertEquals(listOf("org.c"), filterApps(apps, "ORG.", false).map { it.packageName })
    }

    @Test fun sort_idleOldestFirst() {
        val apps = listOf(
            app("new", "New", lastUsed = 50), app("old", "Old", lastUsed = 5),
            app("never", "Never", lastUsed = null, installed = 1)
        )
        assertEquals(listOf("never", "old", "new"), apps.sortedFor(AppSort.IDLE).map { it.packageName })
    }

    @Test fun sort_screenTimeDescendingThenName() {
        val apps = listOf(app("b", "b", fg = 10), app("a", "A", fg = 10), app("c", "C", fg = 99))
        assertEquals(listOf("c", "a", "b"), apps.sortedFor(AppSort.SCREEN_TIME).map { it.packageName })
    }

    @Test fun sort_nameIsCaseInsensitive() {
        val apps = listOf(app("1", "beta"), app("2", "Alpha"), app("3", "Çeta"))
        assertEquals(listOf("2", "1", "3"), apps.sortedFor(AppSort.NAME).map { it.packageName })
    }

    // ---- прочее ----
    @Test fun duration_parts() {
        assertEquals(0L to 0L, durationParts(59_999))
        assertEquals(0L to 1L, durationParts(60_000))
        assertEquals(3L to 20L, durationParts((3 * 60 + 20) * 60_000L))
        assertEquals(0L to 0L, durationParts(-5))
    }

    @Test fun displayName_avoidsDuplicatedManufacturer() {
        assertEquals("Samsung SM-S911B", snapshot().displayName)
        assertEquals("Google Pixel 8", snapshot().copy(manufacturer = "Google", model = "Pixel 8").displayName)
        assertEquals("Google Pixel 8", snapshot().copy(manufacturer = "google", model = "Pixel 8").displayName)
        assertEquals("Xiaomi 13", snapshot().copy(manufacturer = "Xiaomi", model = "Xiaomi 13").displayName)
    }
}
