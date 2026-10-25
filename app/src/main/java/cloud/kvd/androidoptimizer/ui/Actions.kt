package cloud.kvd.androidoptimizer.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.Settings
import android.widget.Toast
import cloud.kvd.androidoptimizer.R

/** Пробует интенты по очереди; если ни один не открылся — показывает короткое сообщение. */
fun Context.openFirstAvailable(vararg intents: Intent) {
    for (intent in intents) {
        if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // пробуем следующий вариант
        } catch (e: SecurityException) {
            // пробуем следующий вариант
        }
    }
    Toast.makeText(this, R.string.toast_open_failed, Toast.LENGTH_SHORT).show()
}

private fun packageUri(packageName: String): Uri = Uri.fromParts("package", packageName, null)

fun storageIntents(): Array<Intent> = arrayOf(
    Intent(StorageManager.ACTION_MANAGE_STORAGE),
    Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    Intent(Settings.ACTION_SETTINGS)
)

fun batteryIntents(): Array<Intent> = arrayOf(
    Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
    Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
    Intent(Settings.ACTION_SETTINGS)
)

/** На Android 10+ экран открывается сразу для нашего приложения; на остальных — общий список. */
fun usageAccessIntents(packageName: String): Array<Intent> = arrayOf(
    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri(packageName)),
    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
    Intent(Settings.ACTION_SETTINGS)
)

fun appDetailsIntents(packageName: String): Array<Intent> = arrayOf(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(packageName))
)

/** Системный диалог удаления: решение всегда принимает пользователь. */
fun uninstallIntents(packageName: String): Array<Intent> = arrayOf(
    Intent(Intent.ACTION_DELETE, packageUri(packageName))
)

/** Экран «Изменение системных настроек» именно для нашего приложения; запасной вариант — общий список. */
fun writeSettingsIntents(packageName: String): Array<Intent> = arrayOf(
    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri(packageName)),
    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS),
    Intent(Settings.ACTION_SETTINGS)
)
