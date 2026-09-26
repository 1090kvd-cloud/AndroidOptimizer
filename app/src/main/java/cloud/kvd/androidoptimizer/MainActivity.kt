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

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme{OptimizerScreen()}}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun OptimizerScreen(){
    val context=LocalContext.current
    var snapshot by remember{mutableStateOf(DeviceDiagnostics.read(context))}
    var profile by remember{mutableStateOf(OptimizationProfile.DAILY)}
    var apps by remember{mutableStateOf(AppAnalyzer.recentApps(context))}
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
            Button(onClick={snapshot=DeviceDiagnostics.read(context);apps=AppAnalyzer.recentApps(context)},modifier=Modifier.fillMaxWidth()){Text("Обновить анализ")}
            Text("No-root режим только анализирует и предлагает безопасные действия. Shizuku/ADB и Root будут отдельными уровнями с проверкой и откатом.")
        }
    }
}
@Composable private fun DiagnosticCard(title:String,value:String){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(title,style=MaterialTheme.typography.labelLarge);Text(value,style=MaterialTheme.typography.bodyLarge)}}}
