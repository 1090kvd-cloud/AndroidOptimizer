package cloud.kvd.androidoptimizer

import android.content.Context
import android.os.Binder
import android.os.Bundle
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Created by Shizuku, not an exported Android Service. */
@Keep
class PackageControlService @Keep constructor(private val context: Context) : IPackageControl.Stub() {
    private val ownerUid = context.applicationInfo.uid

    @Synchronized
    override fun changePackage(packageName: String, userId: Int, enabled: Boolean): Bundle {
        val caller = Binder.getCallingUid()
        if (caller != ownerUid || userId != ownerUid / 100000)
            return result(false, "Недопустимый пользователь.")
        // Dynamic roles and settings are checked in the ordinary app process before binding.
        // The privileged boundary independently blocks core packages and validates all arguments.
        val outcome = PackageCommands(::runCommand).change(packageName, userId, enabled)
        return result(outcome.success, outcome.message)
    }

    private fun runCommand(args: List<String>): PackageCommands.Output {
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = thread(isDaemon = true, name = "package-command-output") {
            runCatching {
                process.inputStream.bufferedReader().use { stream ->
                    val buffer = CharArray(1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        synchronized(output) {
                            if (output.length < 16384) output.append(buffer, 0, minOf(count, 16384 - output.length))
                        }
                    }
                }
            }
        }
        try {
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw java.io.IOException("Время ожидания команды истекло")
            }
            reader.join(1000)
            return PackageCommands.Output(process.exitValue(), synchronized(output) { output.toString() })
        } finally {
            process.destroy()
            runCatching { process.inputStream.close() }
        }
    }

    override fun destroy() { kotlin.system.exitProcess(0) }
    private fun result(success: Boolean, message: String) = Bundle().apply {
        putBoolean("success", success)
        putString("message", message)
    }
}
