package cloud.kvd.androidoptimizer.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import cloud.kvd.androidoptimizer.domain.BatteryState
import cloud.kvd.androidoptimizer.domain.DeviceSnapshot

/** Чтение состояния устройства. Блокирующие вызовы — запускать вне главного потока. */
object DeviceDiagnostics {

    fun read(context: Context): DeviceSnapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)

        val stat = StatFs(Environment.getDataDirectory().path)

        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            totalRamBytes = memory.totalMem,
            availableRamBytes = memory.availMem,
            isLowMemory = memory.lowMemory,
            totalStorageBytes = stat.totalBytes,
            freeStorageBytes = stat.availableBytes,
            battery = readBattery(context)
        )
    }

    /**
     * Sticky-интент ACTION_BATTERY_CHANGED надёжнее BATTERY_PROPERTY_CAPACITY:
     * тот на части устройств возвращает 0 или Int.MIN_VALUE.
     */
    private fun readBattery(context: Context): BatteryState {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else null

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val temperature = if (rawTemp != Int.MIN_VALUE) rawTemp / 10f else null

        return BatteryState(percent = percent, isCharging = charging, temperatureC = temperature)
    }
}
