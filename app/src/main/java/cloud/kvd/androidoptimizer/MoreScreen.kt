package cloud.kvd.androidoptimizer

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun MoreScreen(shizuku: ShizukuState, usageGranted: Boolean, journal: List<ChangeRecord>,
    before: OptimizationSnapshot?, comparison: SnapshotComparison?, onOpen: (Intent) -> Unit,
    onRefresh: () -> Unit, onPermission: () -> Unit, onBefore: () -> Unit, onAfter: () -> Unit, onClearJournal: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeading("Доступ и настройки", "AndroidOptimizer 0.5.0") }
        item {
            SettingsCard("Статистика приложений") {
                Text(if (usageGranted) "Доступ разрешён. Показываем активность за последние 7 дней." else
                    "Список приложений работает без разрешения. Для времени использования нужен доступ к статистике.")
                OutlinedButton(onClick = { onOpen(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Настроить доступ") }
            }
        }
        item {
            SettingsCard("Подключение Shizuku") {
                Text(when (shizuku) {
                    ShizukuState.READY -> "Подключён · разрешение получено"
                    ShizukuState.RUNNING_PERMISSION_NEEDED -> "Запущен · требуется разрешение"
                    ShizukuState.UNAVAILABLE -> "Не подключён"
                }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text("Shizuku — отдельное приложение для доступа к системным API с правами ADB/root. Поиск и проверка приложений работают без него.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Отключение и повторное включение доступны в разделе «Приватность» после подтверждения. Защищённые компоненты отключать нельзя.", style = MaterialTheme.typography.bodySmall)
                if (shizuku == ShizukuState.UNAVAILABLE) {
                    Text("1. Установите Shizuku.\n2. В его инструкции выполните сопряжение через беспроводную отладку (Android 11+).\n3. Запустите сервис и вернитесь сюда.\nНа Android 8–10 для запуска нужен компьютер или root.")
                    Button(onClick = { onOpen(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))) }) { Text("Скачать Shizuku") }
                    OutlinedButton(onClick = { onOpen(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }) { Text("Настройки разработчика") }
                }
                if (shizuku == ShizukuState.RUNNING_PERMISSION_NEEDED) Button(onClick = onPermission) { Text("Разрешить доступ") }
                TextButton(onClick = onRefresh) { Text("Проверить подключение") }
            }
        }
        item {
            SettingsCard("Сравнение до / после") {
                Text("Снимите показатели, выполните нужные действия и повторите замер. Разница не доказывает ускорение телефона.",
                    style = MaterialTheme.typography.bodySmall)
                before?.let { Text("Первый замер: ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.capturedAt))}") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBefore, modifier = Modifier.weight(1f)) { Text("Замер ДО") }
                    Button(onClick = onAfter, enabled = before != null, modifier = Modifier.weight(1f)) { Text("ПОСЛЕ") }
                }
                comparison?.let {
                    Text("Свободная ОЗУ: ${signed(it.ramDeltaMb)} МБ\nСвободное место: ${signed(it.storageDeltaGb)} ГБ\nЗаряд: ${signed(it.batteryDeltaPercent.toLong())}%")
                }
            }
        }
        item {
            SectionHeading("История действий", "Изменения через Shizuku записываются после проверки результата")
            if (journal.isEmpty()) Text("История пока пуста", Modifier.padding(top = 12.dp))
            else TextButton(onClick = onClearJournal) { Text("Очистить историю") }
        }
        items(journal.take(50), key = { it.id }) { record ->
            SettingsCard(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.timestamp))) {
                Text(record.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
private fun signed(value: Long) = if (value > 0) "+$value" else value.toString()
