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
    Scaffold(topBar={TopAppBar(title={Text("AndroidOptimizer")})},bottomBar={NavigationBar{listOf("Главная","Приложения","Privacy","Ещё").forEachIndexed{i,label->NavigationBarItem(selected=section==i,onClick={section=i},icon={Text(listOf("⌂","▦","◈","•••")[i])},label={Text(label)})}}}){padding->
        Column(Modifier.padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            if(section==0){
            Text("Состояние устройства",style=MaterialTheme.typography.headlineSmall)
            DiagnosticCard("Устройство",cleanDeviceName(snapshot.manufacturer,snapshot.model))
            DiagnosticCard("Android",snapshot.androidVersion)
            DiagnosticCard("ОЗУ",snapshot.availableRamMb.toString()+" МБ свободно / "+snapshot.totalRamMb+" МБ")
            DiagnosticCard("Хранилище",snapshot.freeStorageGb.toString()+" ГБ свободно / "+snapshot.totalStorageGb+" ГБ")
            DiagnosticCard("Батарея",snapshot.batteryPercent.toString()+"%")

            Button(onClick={snapshot=DeviceDiagnostics.read(context);apps=AppAnalyzer.recentApps(context);refreshShizuku();journal=ChangeJournal.read(context)},modifier=Modifier.fillMaxWidth()){Text("Анализировать устройство")}
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
                    val eligibility=SafeAppActions.evaluate(context,app.packageName)
                    Card(Modifier.fillMaxWidth()){
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Text(app.label,style=MaterialTheme.typography.labelLarge)
                            Text(minutes.toString()+" мин · "+eligibility.reason)
                            if(eligibility.allowed){
                                OutlinedButton(onClick={
                                    OptimizationEngine.openAppDetails(context,app.packageName)
                                    ChangeJournal.add(context,"Открыты настройки: "+app.packageName)
                                    journal=ChangeJournal.read(context)
                                }){Text("Настройки приложения")}
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("Privacy & Debloat",style=MaterialTheme.typography.titleLarge)
            Text("Сканер ищет кандидатов для проверки. Он не объявляет приложение шпионским только по имени пакета или разрешениям.")
            Button(onClick={debloatFindings=PrivacyDebloatScanner.scan(context);showDebloat=true},modifier=Modifier.fillMaxWidth()){Text("Сканировать системные приложения")}
            if(showDebloat){
                val review=debloatFindings.filter{it.recommendation==DebloatRecommendation.REVIEW}
                Text("Кандидатов для ручной проверки: "+review.size)
                review.take(20).forEach{finding->
                    Card(Modifier.fillMaxWidth()){
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                            Text(finding.label,style=MaterialTheme.typography.titleMedium)
                            Text(finding.packageName)
                            Text(finding.reasons.joinToString(" · "))
                            val plan=PackageStateController.plan(context,finding)
                            Text(plan.reason,style=MaterialTheme.typography.labelMedium)
                            if(plan.canOfferDisable){
                                Button(onClick={
                                    ChangeJournal.add(context,"Перед отключением: "+finding.packageName)
                                    journal=ChangeJournal.read(context)
                                    PackageStateController.openSystemAppPage(context,finding.packageName)
                                }){Text("Отключить в Android")}
                            } else if(plan.canOfferRestore){
                                OutlinedButton(onClick={
                                    ChangeJournal.add(context,"Перед восстановлением: "+finding.packageName)
                                    journal=ChangeJournal.read(context)
                                    PackageStateController.openSystemAppPage(context,finding.packageName)
                                }){Text("Восстановить в Android")}
                            } else {
                                OutlinedButton(onClick={PackageStateController.openSystemAppPage(context,finding.packageName)}){Text("Проверить настройки")}
                            }
                        }
                    }
                }
                val protectedCount=debloatFindings.count{it.category==DebloatCategory.PROTECTED}
                Text("Защищённых системных пакетов: "+protectedCount)
            }

            HorizontalDivider()
            Text("Настройка Shizuku",style=MaterialTheme.typography.titleLarge)
            DiagnosticCard("Расширенный режим",when(shizukuState){
                ShizukuState.READY->"Активен"
                ShizukuState.RUNNING_PERMISSION_NEEDED->"Shizuku запущен — требуется разрешение"
                ShizukuState.UNAVAILABLE->"Не подключён — базовые функции продолжают работать"
            })
            if(shizukuState==ShizukuState.UNAVAILABLE){
                Text("1. Установи Shizuku.\n2. Включи «Для разработчиков» и «Беспроводная отладка».\n3. Запусти Shizuku через сопряжение.\n4. Вернись сюда и проверь подключение.")
                Button(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://shizuku.rikka.app/download/"))) }},modifier=Modifier.fillMaxWidth()){Text("Установить Shizuku")}
                OutlinedButton(onClick={runCatching{context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))}},modifier=Modifier.fillMaxWidth()){Text("Параметры разработчика")}
            }
            if(shizukuState==ShizukuState.RUNNING_PERMISSION_NEEDED){
                Button(onClick={ShizukuAccess.requestPermission()},modifier=Modifier.fillMaxWidth()){Text("Разрешить AndroidOptimizer")}
            }
            OutlinedButton(onClick=refreshShizuku,modifier=Modifier.fillMaxWidth()){Text("Проверить подключение")}
            Text("Shizuku даёт расширенный доступ к системным API через права ADB/root. Сам AndroidOptimizer root не получает.",style=MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            Text("Журнал",style=MaterialTheme.typography.titleLarge)
            if(journal.isEmpty()) Text("Изменений и действий пока нет.")
            journal.take(10).forEach { record ->
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(record.description)
                        Text(if(record.type==ChangeType.REVERSIBLE_CHANGE)"Можно откатить" else "Информационная запись",style=MaterialTheme.typography.labelMedium)
                        if(record.type==ChangeType.REVERSIBLE_CHANGE){
                            OutlinedButton(onClick={
                                val result=RollbackManager.rollback(context,record)
                                if(result.success){ChangeJournal.remove(context,record.id);journal=ChangeJournal.read(context)}
                            }){Text("Откатить")}
                        }
                    }
                }
            }
            if(journal.isNotEmpty()){
                TextButton(onClick={ChangeJournal.clear(context);journal=emptyList()}){Text("Очистить журнал")}
            }

            Button(onClick={snapshot=DeviceDiagnostics.read(context);apps=AppAnalyzer.recentApps(context);refreshShizuku();journal=ChangeJournal.read(context)},modifier=Modifier.fillMaxWidth()){Text("Обновить анализ")}
            Text("Расширенный доступ включается только после явного разрешения Shizuku. Автоматические системные изменения пока не выполняются.")
        }
    }
}
@Composable private fun DiagnosticCard(title:String,value:String){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(title,style=MaterialTheme.typography.labelLarge);Text(value,style=MaterialTheme.typography.bodyLarge)}}}

private fun signed(value:Long):String = if(value>0) "+$value" else value.toString()
\nprivate fun cleanDeviceName(manufacturer:String,model:String):String{val m=manufacturer.trim();val d=model.trim();return if(d.startsWith(m,ignoreCase=true)) d else "$m $d"}\n