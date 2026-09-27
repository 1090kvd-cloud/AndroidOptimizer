package cloud.kvd.androidoptimizer

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Composable
fun ApplicationsScreen(apps: List<AppUsageInfo>, loading: Boolean, error: String?, usageGranted: Boolean,
    onRefresh: () -> Unit, onUsageAccess: () -> Unit, onApp: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val visible = remember(apps, query, filter) {
        apps.filter { app ->
            (filter == 0 || (filter == 1 && !app.isSystem) || (filter == 2 && app.isSystem)) &&
                (app.label.contains(query.trim(), true) || app.packageName.contains(query.trim(), true))
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader("Приложения", "Все установленные приложения", loading, onRefresh)
        SearchField(query, { query = it })
        FilterRow(listOf("Все", "Мои", "Системные"), filter) { filter = it }
        if (!usageGranted) {
            TextButton(onClick = onUsageAccess, contentPadding = PaddingValues(vertical = 4.dp)) {
                Icon(Icons.Outlined.QueryStats, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Разрешить статистику использования")
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) {
            EmptyResult(error, onRefresh)
        } else {
            Text("Найдено: ${visible.size}" + if (usageGranted) " · активность за 7 дней" else " · без статистики активности",
                Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (visible.isEmpty() && !loading) item { EmptyResult("Ничего не найдено. Измените поиск или фильтр.") }
                items(visible, key = { it.packageName }) { app ->
                    Card(onClick = { onApp(app.packageName) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(app.packageName, app.label)
                            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val activity = if (usageGranted) " · ${TimeUnit.MILLISECONDS.toMinutes(app.foregroundMs)} мин" else ""
                                Text((if (app.isSystem) "Системное" else "Установлено вами") + activity,
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.Outlined.ChevronRight, "Открыть настройки", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyScreen(findings: List<SystemAppFinding>, loading: Boolean, error: String?, onRefresh: () -> Unit, onApp: (String) -> Unit,
    shizuku: ShizukuState, changingPackage: String?, onSetup: () -> Unit, onChange: (String, Boolean) -> Unit) {
    var pending by remember { mutableStateOf<SystemAppFinding?>(null) }
    pending?.let { finding ->
        PackageChangeConfirmation(finding, onDismiss = { pending = null }, onConfirm = {
            pending = null
            onChange(finding.packageName, !finding.enabled)
        })
    }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val visible = remember(findings, query, filter) {
        findings.filter { finding ->
            (when (filter) { 1 -> true; 2 -> !finding.enabled; else -> finding.recommendation == DebloatRecommendation.REVIEW }) &&
                (finding.label.contains(query.trim(), true) || finding.packageName.contains(query.trim(), true))
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader("Проверка конфиденциальности", "Системные приложения", loading, onRefresh)
        Text("Признаки аналитики и рекомендации производителя — повод для проверки, а не доказательство слежки.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        SearchField(query, { query = it })
        FilterRow(listOf("На проверку", "Все системные", "Отключённые"), filter) { filter = it }
        if (shizuku != ShizukuState.READY) TextButton(onClick = onSetup) {
            Text(if (shizuku == ShizukuState.UNAVAILABLE) "Подключить Shizuku для отключения" else "Разрешить доступ Shizuku")
        }
        if (changingPackage != null) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) EmptyResult(error, onRefresh)
        else {
            Text(if (loading) "Проверяем установленные пакеты…" else "Проверено ${findings.size} · в списке ${visible.size}",
                Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.labelMedium)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (visible.isEmpty() && !loading) item {
                    EmptyResult(if (query.isNotBlank()) "Нет совпадений по запросу." else if (filter == 2) "Отключённых приложений нет." else
                        "Совпадений по правилам не найдено. Можно открыть список всех системных приложений.")
                }
                items(visible, key = { it.packageName }) { finding ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(finding.packageName, finding.label)
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(finding.label, fontWeight = FontWeight.SemiBold)
                                    Text(finding.packageName, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val protected = finding.category == DebloatCategory.PROTECTED
                            Text(when {
                                !finding.enabled -> "Отключено в Android"
                                protected -> "Системный компонент · сохранить"
                                finding.recommendation == DebloatRecommendation.REVIEW -> "Требует ручной проверки"
                                else -> "Назначение не определено"
                            }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            finding.reasons.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            if (!protected || !finding.enabled) {
                                Button(onClick = { pending = finding },
                                    enabled = !loading && changingPackage == null && shizuku == ShizukuState.READY,
                                    modifier = Modifier.fillMaxWidth()) {
                                    Text(if (changingPackage == finding.packageName) "Применяем…" else
                                        if (finding.enabled) "Отключить" else "Включить обратно")
                                }
                                TextButton(onClick = { onApp(finding.packageName) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Настройки приложения")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PackageChangeConfirmation(finding: SystemAppFinding, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (finding.enabled) "Отключить приложение?" else "Включить приложение?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(finding.label, fontWeight = FontWeight.Bold)
            Text(finding.packageName, style = MaterialTheme.typography.bodySmall)
            Text(if (finding.enabled)
                "Приложение перестанет работать для текущего пользователя. Связанные функции телефона могут стать недоступны. Данные сохранятся; вернуть приложение можно в разделе «Отключённые»."
                else "Приложение снова сможет запускаться и работать в фоне.")
        } },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Подтвердить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
fun ScreenHeader(title: String, subtitle: String, loading: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { SectionHeading(title, subtitle) }
        IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Outlined.Refresh, "Обновить") }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = query, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        shape = RoundedCornerShape(16.dp), label = { Text("Название или пакет") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, "Очистить поиск") } })
}

@Composable
private fun FilterRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, title -> FilterChip(selected == index, { onSelect(index) }, label = { Text(title) }) }
    }
}

@Composable
fun EmptyResult(message: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        onRetry?.let { OutlinedButton(onClick = it) { Text("Повторить") } }
    }
}

@Composable
private fun AppIcon(packageName: String, label: String) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
            ?: Text(label.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
    }
}
