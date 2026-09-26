package cloud.kvd.androidoptimizer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

enum class DebloatCategory { TELEMETRY_CANDIDATE, ADVERTISING_CANDIDATE, OPTIONAL_SYSTEM_APP, PROTECTED }
enum class DebloatRecommendation { REVIEW, KEEP }

data class SystemAppFinding(
    val label:String,
    val packageName:String,
    val category:DebloatCategory,
    val recommendation:DebloatRecommendation,
    val reasons:List<String>
)

object PrivacyDebloatScanner {
    private val protectedPrefixes = listOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.providers",
        "com.google.android.gms"
    )
    private val telemetryHints = listOf("analytics","telemetry","metrics","feedback","diagnostic")
    private val advertisingHints = listOf("ads","advert","recommend","promotion")

    fun scan(context:Context):List<SystemAppFinding> {
        val pm=context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            .map { info ->
                val pkg=info.packageName.lowercase()
                val label=pm.getApplicationLabel(info).toString()
                val protected=protectedPrefixes.any { pkg==it || pkg.startsWith(it+".") }
                val requested=runCatching {
                    pm.getPackageInfo(info.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions?.toList().orEmpty()
                }.getOrDefault(emptyList())
                val reasons=mutableListOf<String>()
                val category=when {
                    protected -> { reasons += "Критический или инфраструктурный пакет"; DebloatCategory.PROTECTED }
                    telemetryHints.any(pkg::contains) -> { reasons += "Имя пакета содержит признак аналитики/диагностики"; DebloatCategory.TELEMETRY_CANDIDATE }
                    advertisingHints.any(pkg::contains) -> { reasons += "Имя пакета содержит признак рекламы/рекомендаций"; DebloatCategory.ADVERTISING_CANDIDATE }
                    else -> { reasons += "Предустановленное системное приложение"; DebloatCategory.OPTIONAL_SYSTEM_APP }
                }
                if(requested.any { it=="android.permission.ACCESS_FINE_LOCATION" }) reasons += "Запрашивает точную геолокацию"
                if(requested.any { it=="android.permission.READ_CONTACTS" }) reasons += "Запрашивает контакты"
                if(requested.any { it=="android.permission.RECORD_AUDIO" }) reasons += "Запрашивает микрофон"
                SystemAppFinding(label,info.packageName,category,if(protected) DebloatRecommendation.KEEP else DebloatRecommendation.REVIEW,reasons)
            }
            .sortedWith(compareBy<SystemAppFinding>{it.recommendation}.thenBy{it.label.lowercase()})
    }
}
