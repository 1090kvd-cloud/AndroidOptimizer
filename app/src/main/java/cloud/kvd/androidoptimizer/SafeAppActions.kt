package cloud.kvd.androidoptimizer

import android.content.Context
import android.content.pm.ApplicationInfo

data class AppActionEligibility(
    val packageName: String,
    val allowed: Boolean,
    val reason: String
)

object SafeAppActions {
    private val protectedPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings"
    )

    fun evaluate(context: Context, packageName: String): AppActionEligibility {
        if (packageName in protectedPackages) {
            return AppActionEligibility(packageName, false, "Критический системный пакет")
        }
        val info = runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
            ?: return AppActionEligibility(packageName, false, "Приложение не найдено")
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            return AppActionEligibility(packageName, false, "Системное приложение")
        }
        return AppActionEligibility(packageName, true, "Пользовательское приложение")
    }
}
