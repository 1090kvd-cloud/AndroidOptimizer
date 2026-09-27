package cloud.kvd.androidoptimizer

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

data class PackageChangeResult(val success: Boolean, val message: String)

object PackageControl {
    private val mutex = Mutex()

    suspend fun change(context: Context, pkg: String, enabled: Boolean): PackageChangeResult = mutex.withLock {
        if (ShizukuAccess.state() != ShizukuState.READY)
            return@withLock PackageChangeResult(false, "Запустите Shizuku и разрешите доступ AndroidOptimizer.")
        if (runCatching { Shizuku.getVersion() }.getOrDefault(0) < 13)
            return@withLock PackageChangeResult(false, "Обновите Shizuku до версии 13 или новее.")
        if (!enabled) {
            val reason = withContext(Dispatchers.IO) { PackageSafety.blockedReason(context, pkg) }
            if (reason != null) return@withLock PackageChangeResult(false, reason)
        }
        val args = Shizuku.UserServiceArgs(ComponentName(context, PackageControlService::class.java))
            .daemon(false).processNameSuffix("package_control").tag("package-control").version(1)
        val connected = CompletableDeferred<IPackageControl>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (binder.pingBinder()) connected.complete(IPackageControl.Stub.asInterface(binder))
                else connected.completeExceptionally(IllegalStateException("Служба недоступна"))
            }
            override fun onServiceDisconnected(name: ComponentName) {
                connected.completeExceptionally(IllegalStateException("Связь с Shizuku потеряна"))
            }
        }
        try {
            Shizuku.bindUserService(args, connection)
            val service = withTimeout(12000) { connected.await() }
            val result = withContext(Dispatchers.IO) { service.changePackage(pkg, Process.myUid() / 100000, enabled) }
            val success = result.getBoolean("success", false)
            val message = result.getString("message") ?: "Нет ответа от службы. Обновите список."
            if (success) withContext(Dispatchers.IO) {
                ChangeJournal.add(context, "${if (enabled) "Включено" else "Отключено"}: $pkg", ChangeType.REVERSIBLE_CHANGE)
            }
            PackageChangeResult(success, message)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            PackageChangeResult(false, "Не удалось подтвердить изменение. Проверьте Shizuku и обновите список.")
        } finally {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
        }
    }
}
