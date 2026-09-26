package cloud.kvd.androidoptimizer

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

enum class ShizukuState { UNAVAILABLE, RUNNING_PERMISSION_NEEDED, READY }

object ShizukuAccess {
    const val REQUEST_CODE = 4107

    fun state(): ShizukuState = try {
        if (!Shizuku.pingBinder()) ShizukuState.UNAVAILABLE
        else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) ShizukuState.READY
        else ShizukuState.RUNNING_PERMISSION_NEEDED
    } catch (_: Throwable) {
        ShizukuState.UNAVAILABLE
    }

    fun requestPermission() {
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    fun serverUid(): Int? = if (state() == ShizukuState.READY) runCatching { Shizuku.getUid() }.getOrNull() else null
}
