package cloud.kvd.androidoptimizer

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager

object PackageSafety {
    fun blockedReason(context: Context, pkg: String): String? {
        if (PackageCommands.isProtected(pkg) || OemDebloatDatabase.find(pkg)?.risk == OemRisk.PROTECTED)
            return "Защищённый системный компонент"
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)
            when {
                info.uid % 100000 < 10000 -> "Системный UID — отключение заблокировано"
                info.flags and ApplicationInfo.FLAG_PERSISTENT != 0 -> "Постоянная системная служба"
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                    .any { it.activityInfo.packageName == pkg } -> "Домашний экран"
                context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == pkg -> "Приложение звонков по умолчанию"
                Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                    ?.let { ComponentName.unflattenFromString(it)?.packageName } == pkg -> "Текущая клавиатура"
                context.getSystemService(DevicePolicyManager::class.java)?.activeAdmins.orEmpty()
                    .any { it.packageName == pkg } -> "Активный администратор устройства"
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    .orEmpty().split(':').any { ComponentName.unflattenFromString(it)?.packageName == pkg } -> "Включённая служба специальных возможностей"
                else -> null
            }
        } catch (_: Exception) { "Не удалось проверить защиту пакета. Отключение недоступно." }
    }
}
