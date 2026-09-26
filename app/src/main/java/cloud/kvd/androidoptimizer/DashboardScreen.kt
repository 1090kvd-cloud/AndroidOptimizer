package cloud.kvd.androidoptimizer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun DashboardScreen(
    device: DeviceSnapshot?, appCount: Int, loading: Boolean, profile: OptimizationProfile,
    onProfile: (OptimizationProfile) -> Unit, onRefresh: () -> Unit,
    onApps: () -> Unit, onPrivacy: () -> Unit, onAccess: () -> Unit,
    onStorage: () -> Unit, onBattery: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AndroidOptimizer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(device?.let { cleanDeviceName(it.manufacturer, it.model) } ?: "Состояние устройства",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Outlined.Refresh, "Обновить показатели") }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF2868F5), Color(0xFF1846BD))), RoundedCornerShape(28.dp)
            ).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("ПАМЯТЬ УСТРОЙСТВА", color = Color.White.copy(alpha = .8f),
                    style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
                val total = device?.totalStorageGb ?: 0
                val free = device?.freeStorageGb ?: 0
                val used = (total - free).coerceAtLeast(0)
                val fraction = if (total > 0) used.toFloat() / total else 0f
                Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(), color = Color.White,
                        trackColor = Color.White.copy(alpha = .18f), strokeWidth = 10.dp, strokeCap = StrokeCap.Round)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (device == null) "—" else "${(fraction * 100).toInt()}%",
                            fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("занято", color = Color.White.copy(alpha = .8f))
                    }
                }
                Text(if (device == null) "Считываем показатели…" else "Свободно $free ГБ из $total ГБ",
                    color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onRefresh, enabled = !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF245DDC),
                        disabledContainerColor = Color.White.copy(alpha = .8f), disabledContentColor = Color(0xFF245DDC))) {
                    Icon(Icons.Outlined.Search, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (loading) "Анализируем…" else "Проверить устройство", fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("ОЗУ свободно", device?.let { "${formatGb(it.availableRamMb)} ГБ" } ?: "—",
                    device?.let { "из ${formatGb(it.totalRamMb)} ГБ" } ?: "Загрузка", Icons.Outlined.Memory, Modifier.weight(1f))
                StatCard("Батарея", device?.batteryPercent?.takeIf { it in 0..100 }?.let { "$it%" } ?: "—",
                    "Заряд устройства", Icons.Outlined.BatteryFull, Modifier.weight(1f))
            }
        }
        item {
            SectionHeading("Инструменты", "Выберите, что проверить")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCard("Хранилище", "Файлы и место", Icons.Outlined.Folder, OptimizerBlue, Modifier.weight(1f), onStorage)
                ToolCard("Приложения", if (loading) "Загрузка списка" else "$appCount установлено",
                    Icons.Outlined.Apps, OptimizerTeal, Modifier.weight(1f), onApps)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCard("Приватность", "Системные пакеты", Icons.Outlined.Shield, Color(0xFF8B5DCE), Modifier.weight(1f), onPrivacy)
                ToolCard("Батарея", "Энергосбережение", Icons.Outlined.Bolt, Color(0xFFB7740D), Modifier.weight(1f), onBattery)
            }
        }
        item {
            SectionHeading("Рекомендации", "Под вашу задачу")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptimizationProfile.entries.forEach { option ->
                    FilterChip(selected = profile == option, onClick = { onProfile(option) }, label = { Text(option.title) })
                }
            }
            OptimizationEngine.recommendations(profile).forEach { action ->
                Card(onClick = { when (action.id) { "battery" -> onBattery(); "shizuku" -> onAccess(); else -> onApps() } },
                    modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(action.title, fontWeight = FontWeight.SemiBold)
                            Text(action.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 12.dp).size(20.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun SectionHeading(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun StatCard(title: String, value: String, detail: String, icon: ImageVector, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToolCard(title: String, detail: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.background(tint.copy(alpha = .12f), RoundedCornerShape(14.dp)).padding(10.dp)) { Icon(icon, null, tint = tint) }
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatGb(mb: Long) = String.format(Locale.getDefault(), "%.1f", mb / 1024.0)
private fun cleanDeviceName(manufacturer: String, model: String): String =
    if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
