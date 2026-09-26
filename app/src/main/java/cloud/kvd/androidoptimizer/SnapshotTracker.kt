package cloud.kvd.androidoptimizer

import android.content.Context

data class OptimizationSnapshot(
    val capturedAt: Long,
    val availableRamMb: Long,
    val freeStorageGb: Long,
    val batteryPercent: Int
)

data class SnapshotComparison(
    val ramDeltaMb: Long,
    val storageDeltaGb: Long,
    val batteryDeltaPercent: Int
)

object SnapshotTracker {
    fun capture(context: Context): OptimizationSnapshot {
        val d = DeviceDiagnostics.read(context)
        return OptimizationSnapshot(
            capturedAt = System.currentTimeMillis(),
            availableRamMb = d.availableRamMb,
            freeStorageGb = d.freeStorageGb,
            batteryPercent = d.batteryPercent
        )
    }

    fun compare(before: OptimizationSnapshot, after: OptimizationSnapshot) = SnapshotComparison(
        ramDeltaMb = after.availableRamMb - before.availableRamMb,
        storageDeltaGb = after.freeStorageGb - before.freeStorageGb,
        batteryDeltaPercent = after.batteryPercent - before.batteryPercent
    )
}
