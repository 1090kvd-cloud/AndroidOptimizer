package cloud.kvd.androidoptimizer

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process

data class AppUsageInfo(val label:String,val packageName:String,val lastUsed:Long,val foregroundMs:Long,val isSystem:Boolean)

object AppAnalyzer {
    fun hasUsageAccess(context: Context): Boolean {
        val ops=context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName)==AppOpsManager.MODE_ALLOWED
    }
    fun installedApps(context:Context):List<AppUsageInfo>{
        val pm=context.packageManager
        return pm.getInstalledApplications(0).mapNotNull{info->runCatching{
            AppUsageInfo(pm.getApplicationLabel(info).toString(),info.packageName,0L,0L,info.flags and ApplicationInfo.FLAG_SYSTEM != 0)
        }.getOrNull()}.sortedBy{it.label.lowercase()}
    }
    fun recentApps(context: Context, days:Int=7): List<AppUsageInfo> {
        if(!hasUsageAccess(context)) return installedApps(context)
        val now=System.currentTimeMillis()
        val manager=context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm=context.packageManager
        val usage=manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,now-days*86400000L,now)
            .filter{it.totalTimeInForeground>0}.associateBy{it.packageName}
        return installedApps(context).map{app->
            val u=usage[app.packageName]
            app.copy(lastUsed=u?.lastTimeUsed?:0L,foregroundMs=u?.totalTimeInForeground?:0L)
        }.sortedWith(compareByDescending<AppUsageInfo>{it.foregroundMs}.thenBy{it.label.lowercase()})
    }
}