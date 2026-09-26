package cloud.kvd.androidoptimizer

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContent{MaterialTheme{OptimizerScreen()}}
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun OptimizerScreen(){
    val context=LocalContext.current
    var snapshot by remember{mutableStateOf(DeviceDiagnostics.read(context))}
    var profile by remember{mutableStateOf(OptimizationProfile.DAILY)}
    var apps by remember{mutableStateOf(AppAnalyzer.recentApps(context))}
    var shizukuState by remember{mutableStateOf(ShizukuAccess.state())}
    var beforeSnapshot by remember { mutableStateOf<OptimizationSnapshot?>(null) }
    var comparison by remember { mutableStateOf<SnapshotComparison?>(null) }
    val refreshShizuku={shizukuState=ShizukuAccess.state()}
    DisposableEffect(Unit){
        val received=Shizuku.OnBinderReceivedListener{refreshShizuku()}
        val dead=Shizuku.OnBinderDeadListener{refreshShizuku()}
        val permission=Shizuku.OnRequestPermissionResultListener{requestCode,_->if(requestCode==ShizukuAccess.REQUEST_CODE)refreshShizuku()}
        Shizuku.addBinderReceivedListener(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        onDispose{
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
        }
    }
    val usageGranted=AppAnalyzer.hasUsageAccess(context)
    Scaffold(topBar={TopAppBar(title={Text("AndroidOptimizer")})}){padding->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Состояние устройства",style=MaterialTheme.typography.headlineSmall)
            DiagnosticCard("Устройство",snapshot.manufacturer+" "+snapshot.model)
            DiagnosticCard("Android",snapshot.androidVersion)
            DiagnosticCard("ОЗУ",snapshot.availableRamMb.toString()+" МБ свободно / "+snapshot.totalRamMb+" МБ")
            DiagnosticCard("Хранилище",snapshot.freeStorageGb.toString()+" ГБ свободно / "+snapshot.totalStorageGb+" ГБ")
            DiagnosticCard("Батарея",snapshot.batteryPercent.toString()+"%")

            Text("Профиль",style=MaterialTheme.typography.titleLarge)
            OptimizationProfile.entries.forEach{item->FilterChip(selected=profile==item,onClick={profile=item},label={Text(item.title)})}
            Text(profile.description)
            OptimizationEngine.recommendations(profile).forEach { action ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(action.title, style = MaterialTheme.typography.titleMedium)
                        Text(action.description)
                        when (action.id) {
                            "battery" -> OutlinedButton(onClick = { OptimizationEngine.openBatterySettings(context) }) { Text("Открыть настройки") }
                            "background", "unused_apps" -> apps.firstOrNull { !it.isSystem }?.let { candidate ->
                                OutlinedButton(onClick = { OptimizationEngine.openAppDetails(context, candidate.packageName) }) {
                                    Text("Открыть " + candidate.label)
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("До / После", style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = { beforeSnapshot = SnapshotTracker.capture(context); comparison = null },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Снять показатель ДО") }
            OutlinedButton(
                onClick = {
                    beforeSnapshot?.let { before ->
                        comparison = SnapshotTracker.compare(before, SnapshotTracker.capture(context))
                    }
                },
                enabled = beforeSnapshot != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Снять показатель ПОСЛЕ") }
            comparison?.let { result ->
                DiagnosticCard("Свободная ОЗУ", signed(result.ramDeltaMb) + " МБ")
                DiagnosticCard("Свободное хранилище", signed(result.storageDeltaGb) + " ГБ")
                DiagnosticCard("Заряд между замерами", signed(result.batteryDeltaPercent.toLong()) + "%")
                Text("Показатели описывают изменение между двумя замерами и не доказывают ускорение устройства сами по себе.")
            }

            HorizontalDivider()
            Text("Shizuku / ADB",style=MaterialTheme.typography.titleLarge)
            when(shizukuState){
                ShizukuState.UNAVAILABLE->Text("Shizuku не запущен или недоступен. Запусти Shizuku на устройстве и вернись в приложение.")
                ShizukuState.RUNNING_PERMISSION_NEEDED->Button(onClick={ShizukuAccess.requestPermission()}){Text("Разрешить доступ через Shizuku")}
                ShizukuState.READY->DiagnosticCard("Shizuku готов","UID сервера: "+(ShizukuAccess.serverUid()?.toString()?:"не определён"))
            }
            OutlinedButton(onClick=refreshShizuku,modifier=Modifier.fillMaxWidth()){Text("Проверить Shizuku")}

            HorizontalDivider()
            Text("Активность приложений",style=MaterialTheme.typography.titleLarge)
            if(!usageGranted){
                Text("Для анализа активности нужен системный доступ к статистике использования.")
                Button(onClick={context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))},modifier=Modifier.fillMaxWidth()){Text("Выдать Usage Access")}
            }else{
                if(apps.isEmpty()) Text("Нет данных за последние 7 дней.")
                apps.take(10).forEach{app->
                    val minutes=TimeUnit.MILLISECONDS.toMinutes(app.foregroundMs)
                    DiagnosticCard(app.label,minutes.toString()+" мин · "+if(app.isSystem)"системное" else "пользовательское")
                }
            }
            Button(onClick={snapshot=DeviceDiagnostics.read(context);apps=AppAnalyzer.recentApps(context);refreshShizuku()},modifier=Modifier.fillMaxWidth()){Text("Обновить анализ")}
            Text("Расширенный доступ включается только после явного разрешения Shizuku. Автоматические системные изменения пока не выполняются.")
        }
    }
}
@Composable private fun DiagnosticCard(title:String,value:String){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(title,style=MaterialTheme.typography.labelLarge);Text(value,style=MaterialTheme.typography.bodyLarge)}}}

private fun signed(value:Long):String = if(value>0) "+$value" else value.toString()
