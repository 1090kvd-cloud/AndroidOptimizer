package cloud.kvd.androidoptimizer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

data class PackageStatePlan(
    val packageName:String,
    val canOfferDisable:Boolean,
    val canOfferRestore:Boolean,
    val reason:String
)

object PackageStateController {
    fun plan(context:Context, finding:SystemAppFinding):PackageStatePlan {
        val known=OemDebloatDatabase.find(finding.packageName)
        if(finding.category==DebloatCategory.PROTECTED || known?.risk==OemRisk.PROTECTED) {
            return PackageStatePlan(finding.packageName,false,false,"Защищённый пакет")
        }
        val enabled=runCatching{context.packageManager.getApplicationInfo(finding.packageName,0).enabled}.getOrDefault(true)
        return if(enabled) PackageStatePlan(finding.packageName,true,false,"Можно открыть системный экран для отключения")
        else PackageStatePlan(finding.packageName,false,true,"Пакет сейчас отключён")
    }

    fun openSystemAppPage(context:Context, packageName:String) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))
    }
}
