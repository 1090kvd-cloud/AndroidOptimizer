package cloud.kvd.androidoptimizer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun DashboardScreen(
    device: DeviceSnapshot?, appCount: Int, loading: Boolean, onRefresh: () -> Unit,
    onApps: () -> Unit, onPrivacy: () -> Unit, onAccess: () -> Unit,
    onStorage: () -> Unit, onBattery: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Мой телефон", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(device?.let { cleanDeviceName(it.manufacturer, it.model) } ?: "Состояние устройства",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onAccess) { Icon(Icons.Outlined.Settings, "Настройки") }
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val total = device?.totalStorageGb ?: 0
                val free = device?.freeStorageGb ?: 0
                val fraction = if (total > 0) ((total - free).toFloat() / total).coerceIn(0f, 1f) else 0f
                Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxSize(),
                        color = OptimizerBlue, trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 8.dp, strokeCap = StrokeCap.Round)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (device == null) "—" else "${(fraction * 100).toInt()}%",
                            fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Text("памяти занято", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(device?.let { "ОЗУ свободно ${formatGb(it.availableRamMb)} из ${formatGb(it.totalRamMb)} ГБ" }
                    ?: "Считываем показатели…", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onRefresh, enabled = !loading, modifier = Modifier.heightIn(min = 50.dp)) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Search, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (loading) "Анализируем…" else "Проверить устройство", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCard("Память", device?.let { "${it.freeStorageGb} ГБ свободно" } ?: "Файлы и место",
                    Icons.Outlined.Folder, OptimizerBlue, Modifier.weight(1f), onStorage)
                ToolCard("Приложения", if (loading) "Загрузка списка…" else "$appCount установлено",
                    Icons.Outlined.Apps, OptimizerTeal, Modifier.weight(1f), onApps)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCard("Приватность", "Проверка пакетов", Icons.Outlined.Shield,
                    Color(0xFF9866E8), Modifier.weight(1f), onPrivacy)
                ToolCard("Батарея", device?.batteryPercent?.takeIf { it in 0..100 }?.let { "Заряд $it%" } ?: "Энергосбережение",
                    Icons.Outlined.Bolt, Color(0xFFFFA000), Modifier.weight(1f), onBattery)
            }
        }
    }
}

@Composable
fun StorageScreen(device: DeviceSnapshot?, onStorage: () -> Unit, onFiles: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { SectionHeading("Память телефона") }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Свободное место", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(device?.let { "${it.freeStorageGb} ГБ" } ?: "—", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    val total = device?.totalStorageGb ?: 0
                    val free = device?.freeStorageGb ?: 0
                    LinearProgressIndicator(progress = { if (total > 0) ((total-free).toFloat()/total).coerceIn(0f,1f) else 0f },
                        modifier = Modifier.fillMaxWidth().height(10.dp), color = Color(0xFFFFAB28), strokeCap = StrokeCap.Round)
                    Text(device?.let { "Занято ${it.totalStorageGb-it.freeStorageGb} из ${it.totalStorageGb} ГБ" }
                        ?: "Показатели пока недоступны", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Button(onClick = onStorage, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Управление хранилищем") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onFiles, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Открыть загрузки") }
        }
        item { Text("Просмотрите файлы и выберите, что удалить, в системном приложении. Автоматического удаления нет.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun SectionHeading(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun ToolCard(title: String, detail: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatGb(mb: Long) = String.format(Locale.getDefault(), "%.1f", mb / 1024.0)
private fun cleanDeviceName(manufacturer: String, model: String): String =
    if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
