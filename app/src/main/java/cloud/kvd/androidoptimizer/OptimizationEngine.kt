package cloud.kvd.androidoptimizer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

data class OptimizationAction(
    val id: String,
    val title: String,
    val description: String,
    val reversible: Boolean = true
)

object OptimizationEngine {
    fun recommendations(profile: OptimizationProfile): List<OptimizationAction> = when (profile) {
        OptimizationProfile.DAILY -> listOf(
            OptimizationAction("unused_apps", "Проверить редко используемые приложения", "Удалить или ограничить только те приложения, которые пользователь подтвердит.")
        )
        OptimizationProfile.BATTERY -> listOf(
            OptimizationAction("battery", "Проверить расход батареи", "Открывает штатную страницу Android для ручной проверки."),
            OptimizationAction("background", "Проверить фоновые приложения", "Ограничения применяются только к выбранному пользователем приложению.")
        )
        OptimizationProfile.GAMING -> listOf(
            OptimizationAction("background", "Снизить фоновую нагрузку", "Предлагает проверить активные пользовательские приложения перед игрой.")
        )
        OptimizationProfile.MAX_PERFORMANCE -> listOf(
            OptimizationAction("shizuku", "Проверить расширенный доступ", "Для расширенных действий требуется явно разрешённый Shizuku.")
        )
    }

    fun openAppDetails(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        context.startActivity(intent)
    }

    fun openBatterySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
    }
}
