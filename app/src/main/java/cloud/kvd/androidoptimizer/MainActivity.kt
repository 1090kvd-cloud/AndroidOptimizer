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
    var journal by remember { mutableStateOf(ChangeJournal.read(context)) }
    var debloatFindings by remember { mutableStateOf<List<SystemAppFinding>>(emptyList()) }
    var showDebloat by remember { mutableStateOf(false) }
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
    var section by remember{mutableStateOf(0)}
    LaunchedEffect(section){
        if(section==1) apps=AppAnalyzer.recentApps(context)
        if(section==2){debloatFindings=PrivacyDebloatScanner.scan(context);showDebloat=true}
    }
    Scaffold(
        topBar={TopAppBar(title={Text("AndroidOptimizer")})},
        bottomBar={NavigationBar{
            listOf("Главная","Приложения","Privacy","Ещё").forEachIndexed{i,label->
                NavigationBarItem(selected=section==i,onClick={section=i},icon={Text(listOf("⌂","▦","◈","•••")[i])},label={Text(label)})
            }
        }}
    ){padding->
        Column(Modifier.padding(padding).padding(horizontal=18.dp,vertical=12.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            when(section){
                0 -> {
                    Text("Оптимизация",style=MaterialTheme.typography.headlineMedium)
                    DiagnosticCard("Устройство",cleanDeviceName(snapshot.manufacturer,snapshot.model))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Box(Modifier.weight(1f)){DiagnosticCard("ОЗУ",snapshot.availableRamMb.toString()+" МБ")}
                        Box(Modifier.weight(1f)){DiagnosticCard("Память",snapshot.freeStorageGb.toString()+" ГБ")}
                        Box(Modifier.weight(1f)){DiagnosticCard("Батарея",snapshot.batteryPercent.toString()+"%")}
                    }
                    Button(onClick={snapshot=DeviceDiagnostics.read(context);apps=AppAnalyzer.recentApps(context)},modifier=Modifier.fillMaxWidth()){Text("ПРОВЕРИТЬ УСТРОЙСТВО")}
                    Text("Быстрые действия",style=MaterialTheme.typography.titleLarge)
                    ElevatedCard(onClick={section=1},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text("Приложения",style=MaterialTheme.typography.titleMedium);Text("Найти тяжёлые и редко используемые приложения")}}
                    ElevatedCard(onClick={section=2},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text("Privacy & Debloat",style=MaterialTheme.typography.titleMedium);Text("Проверить предустановленные системные приложения")}}
                    ElevatedCard(onClick={section=3},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text("Shizuku",style=MaterialTheme.typography.titleMedium);Text(if(shizukuState==ShizukuState.READY)"Расширенный режим активен" else "Настроить расширенный доступ")}}
                }
                1 -> {
                    Text("Приложения",style=MaterialTheme.typography.headlineMedium)
                    if(!usageGranted){
                        Text("Список приложений работает без дополнительных разрешений. Usage Access добавляет статистику использования.")
                        OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))},modifier=Modifier.fillMaxWidth()){Text("Включить статистику использования")}
                    }
                    Text("Найдено: "+apps.size,style=MaterialTheme.typography.labelLarge)
                    apps.take(60).forEach{app->
                        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                            Text(app.label,style=MaterialTheme.typography.titleMedium)
                            Text(app.packageName,style=MaterialTheme.typography.bodySmall)
                            if(app.foregroundMs>0) Text(TimeUnit.MILLISECONDS.toMinutes(app.foregroundMs).toString()+" мин использования",style=MaterialTheme.typography.labelMedium)
                            OutlinedButton(onClick={OptimizationEngine.openAppDetails(context,app.packageName)}){Text("Настройки")}
                        }}
                    }
                }
                2 -> {
                    Text("Privacy & Debloat",style=MaterialTheme.typography.headlineMedium)
                    Text("Сканер показывает кандидатов для ручной проверки и защищает критические системные компоненты.")
                    Button(onClick={debloatFindings=PrivacyDebloatScanner.scan(context);showDebloat=true},modifier=Modifier.fillMaxWidth()){Text("ПОВТОРИТЬ СКАНИРОВАНИЕ")}
                    val review=debloatFindings.filter{it.recommendation==DebloatRecommendation.REVIEW}
                    Text("На проверку: "+review.size+" · Защищено: "+debloatFindings.count{it.category==DebloatCategory.PROTECTED})
                    review.take(50).forEach{finding->
                        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                            Text(finding.label,style=MaterialTheme.typography.titleMedium)
                            Text(finding.packageName,style=MaterialTheme.typography.bodySmall)
                            Text(finding.reasons.joinToString(" · "),style=MaterialTheme.typography.bodyMedium)
                            val plan=PackageStateController.plan(context,finding)
                            FilledTonalButton(onClick={PackageStateController.openSystemAppPage(context,finding.packageName)}){Text(if(plan.canOfferRestore)"Открыть для восстановления" else "Проверить / отключить")}
                        }}
                    }
                }
                else -> {
                    Text("Инструменты",style=MaterialTheme.typography.headlineMedium)
                    DiagnosticCard("Shizuku",when(shizukuState){ShizukuState.READY->"Подключён";ShizukuState.RUNNING_PERMISSION_NEEDED->"Нужно разрешение";ShizukuState.UNAVAILABLE->"Не подключён"})
                    if(shizukuState==ShizukuState.UNAVAILABLE){
                        Text("1. Установи Shizuku.\n2. Включи беспроводную отладку.\n3. Выполни сопряжение в Shizuku.\n4. Вернись и проверь подключение.")
                        Button(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://shizuku.rikka.app/download/"))) }},modifier=Modifier.fillMaxWidth()){Text("Установить Shizuku")}
                        OutlinedButton(onClick={runCatching{context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))}},modifier=Modifier.fillMaxWidth()){Text("Параметры разработчика")}
                    }
                    if(shizukuState==ShizukuState.RUNNING_PERMISSION_NEEDED) Button(onClick={ShizukuAccess.requestPermission()},modifier=Modifier.fillMaxWidth()){Text("Разрешить AndroidOptimizer")}
                    OutlinedButton(onClick=refreshShizuku,modifier=Modifier.fillMaxWidth()){Text("Проверить Shizuku")}
                    HorizontalDivider()
                    Text("Журнал",style=MaterialTheme.typography.titleLarge)
                    if(journal.isEmpty()) Text("Изменений пока нет.")
                    journal.take(10).forEach{record->Text("• "+record.description)}
                }
            }
        }
    }
}
@Composable private fun DiagnosticCard(title:String,value:String){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(title,style=MaterialTheme.typography.labelLarge);Text(value,style=MaterialTheme.typography.bodyLarge)}}}

private fun signed(value:Long):String = if(value>0) "+$value" else value.toString()

private fun cleanDeviceName(manufacturer:String,model:String):String{val m=manufacturer.trim();val d=model.trim();return if(d.startsWith(m,ignoreCase=true)) d else "$m $d"}