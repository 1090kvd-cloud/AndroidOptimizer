package cloud.kvd.androidoptimizer

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs

data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val totalStorageGb: Long,
    val freeStorageGb: Long,
    val batteryPercent: Int
)

object DeviceDiagnostics {
    fun read(context: Context): DeviceSnapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalStorage = stat.totalBytes / 1_073_741_824L
        val freeStorage = stat.availableBytes / 1_073_741_824L

        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPercent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            totalRamMb = memory.totalMem / 1_048_576L,
            availableRamMb = memory.availMem / 1_048_576L,
            totalStorageGb = totalStorage,
            freeStorageGb = freeStorage,
            batteryPercent = batteryPercent
        )
    }
}
