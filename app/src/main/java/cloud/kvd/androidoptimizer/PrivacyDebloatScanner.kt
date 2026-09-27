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
    val reasons:List<String>,
    val enabled:Boolean = true
)

object PrivacyDebloatScanner {
    private val telemetryHints = listOf("analytics","telemetry","metrics","feedback","diagnostic")
    private val advertisingHints = listOf("ads","advert","recommend","promotion")

    fun scan(context:Context):List<SystemAppFinding> {
        val pm=context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            .map { info ->
                val pkg=info.packageName.lowercase()
                val label=runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName)
                val known=OemDebloatDatabase.find(info.packageName)
                val protected=PackageCommands.isProtected(info.packageName) || known?.risk==OemRisk.PROTECTED ||
                    info.uid % 100000 < 10000 || info.flags and ApplicationInfo.FLAG_PERSISTENT != 0
                val requested=runCatching {
                    pm.getPackageInfo(info.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions?.toList().orEmpty()
                }.getOrDefault(emptyList())
                val reasons=mutableListOf<String>()
                val category=when {
                    protected -> { reasons += (known?.note ?: "Критический или инфраструктурный пакет"); DebloatCategory.PROTECTED }
                    known?.risk==OemRisk.CAUTION -> { reasons += known.note; DebloatCategory.OPTIONAL_SYSTEM_APP }
                    known?.risk==OemRisk.RECOMMENDED_REVIEW -> { reasons += known.note; DebloatCategory.OPTIONAL_SYSTEM_APP }
                    telemetryHints.any(pkg::contains) -> { reasons += "Имя пакета содержит признак аналитики/диагностики"; DebloatCategory.TELEMETRY_CANDIDATE }
                    advertisingHints.any(pkg::contains) -> { reasons += "Имя пакета содержит признак рекламы/рекомендаций"; DebloatCategory.ADVERTISING_CANDIDATE }
                    else -> { reasons += "Предустановленное системное приложение"; DebloatCategory.OPTIONAL_SYSTEM_APP }
                }
                if(requested.any { it=="android.permission.ACCESS_FINE_LOCATION" }) reasons += "Запрашивает точную геолокацию"
                if(requested.any { it=="android.permission.READ_CONTACTS" }) reasons += "Запрашивает контакты"
                if(requested.any { it=="android.permission.RECORD_AUDIO" }) reasons += "Запрашивает микрофон"
                val hasReviewSignal = known?.risk in listOf(OemRisk.CAUTION, OemRisk.RECOMMENDED_REVIEW) ||
                    category == DebloatCategory.TELEMETRY_CANDIDATE || category == DebloatCategory.ADVERTISING_CANDIDATE
                SystemAppFinding(label,info.packageName,category,
                    if(!protected && hasReviewSignal) DebloatRecommendation.REVIEW else DebloatRecommendation.KEEP,
                    reasons,info.enabled)
            }
            .sortedWith(compareBy<SystemAppFinding>{it.recommendation}.thenBy{it.label.lowercase()})
    }
}
