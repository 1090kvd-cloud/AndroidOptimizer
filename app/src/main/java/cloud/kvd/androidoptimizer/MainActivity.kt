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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { OptimizerScreen() } }
    }
}

@Composable
private fun OptimizerScreen() {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(DeviceDiagnostics.read(context)) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("AndroidOptimizer") }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Диагностика устройства", style = MaterialTheme.typography.headlineSmall)
            DiagnosticCard("Устройство", snapshot.manufacturer + " " + snapshot.model)
            DiagnosticCard("Android", snapshot.androidVersion)
            DiagnosticCard("ОЗУ", snapshot.availableRamMb.toString() + " МБ свободно / " + snapshot.totalRamMb + " МБ")
            DiagnosticCard("Хранилище", snapshot.freeStorageGb.toString() + " ГБ свободно / " + snapshot.totalStorageGb + " ГБ")
            DiagnosticCard("Батарея", snapshot.batteryPercent.toString() + "%")

            Button(onClick = { snapshot = DeviceDiagnostics.read(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Обновить диагностику")
            }
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Доступ к статистике приложений") }

            HorizontalDivider()
            Text("Оптимизация v0.1", style = MaterialTheme.typography.titleLarge)
            Text("Первая версия работает без root: показывает состояние устройства и ведёт к системным настройкам вместо опасного принудительного изменения Android.")
            Text("Далее: анализ приложений, профили Производительность/Батарея, Shizuku и отдельный Root-режим с откатом.")
        }
    }
}

@Composable
private fun DiagnosticCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
