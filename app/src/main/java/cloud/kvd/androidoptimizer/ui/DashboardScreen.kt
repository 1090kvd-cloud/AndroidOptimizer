package cloud.kvd.androidoptimizer.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cloud.kvd.androidoptimizer.R
import cloud.kvd.androidoptimizer.domain.AppSort
import cloud.kvd.androidoptimizer.domain.DeviceSnapshot
import cloud.kvd.androidoptimizer.domain.Level
import cloud.kvd.androidoptimizer.domain.Recommendation
import cloud.kvd.androidoptimizer.domain.RecommendationType
import cloud.kvd.androidoptimizer.domain.batteryChargeLevel
import cloud.kvd.androidoptimizer.domain.batteryTempLevel
import cloud.kvd.androidoptimizer.domain.ramLevel
import cloud.kvd.androidoptimizer.domain.storageLevel
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    state: UiState,
    onOpenApps: (AppSort) -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot = state.snapshot
    if (snapshot == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "device") { DeviceHeader(snapshot) }
        item(key = "ram") { RamCard(snapshot) }
        item(key = "storage") { StorageCard(snapshot) }
        item(key = "battery") { BatteryCard(snapshot) }

        item(key = "reco_title") { SectionTitle(stringResource(R.string.section_recommendations)) }
        if (state.recommendations.isEmpty()) {
            item(key = "reco_none") {
                RecommendationCard(
                    level = Level.NORMAL,
                    title = stringResource(R.string.reco_none_title),
                    body = stringResource(R.string.reco_none_body)
                )
            }
        } else {
            items(state.recommendations, key = { it.type }) { RecommendationItem(it, onOpenApps) }
        }

        item(key = "modes_title") { SectionTitle(stringResource(R.string.section_modes)) }
        item(key = "modes") { ModesCard() }
    }
}

@Composable
private fun DeviceHeader(snapshot: DeviceSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(snapshot.displayName, style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.device_android, snapshot.androidVersion, snapshot.sdkInt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RamCard(snapshot: DeviceSnapshot) {
    val context = LocalContext.current
    val used = (snapshot.ramUsedFraction * 100).roundToInt()
    MetricCard(
        title = stringResource(R.string.metric_ram),
        fraction = snapshot.ramUsedFraction,
        centerText = "$used%",
        headline = stringResource(R.string.metric_used_percent, used),
        detail = stringResource(
            R.string.metric_free_of,
            Formatter.formatFileSize(context, snapshot.availableRamBytes),
            Formatter.formatFileSize(context, snapshot.totalRamBytes)
        ),
        level = ramLevel(snapshot)
    )
}

@Composable
private fun StorageCard(snapshot: DeviceSnapshot) {
    val context = LocalContext.current
    val used = (snapshot.storageUsedFraction * 100).roundToInt()
    MetricCard(
        title = stringResource(R.string.metric_storage),
        fraction = snapshot.storageUsedFraction,
        centerText = "$used%",
        headline = stringResource(R.string.metric_used_percent, used),
        detail = stringResource(
            R.string.metric_free_of,
            Formatter.formatFileSize(context, snapshot.freeStorageBytes),
            Formatter.formatFileSize(context, snapshot.totalStorageBytes)
        ),
        level = storageLevel(snapshot)
    )
}

@Composable
private fun BatteryCard(snapshot: DeviceSnapshot) {
    val battery = snapshot.battery
    val percent = battery.percent
    val headline = when {
        battery.isCharging -> stringResource(R.string.battery_charging)
        percent == null -> stringResource(R.string.battery_unknown)
        else -> stringResource(R.string.battery_discharging)
    }
    val level = if (batteryTempLevel(battery).ordinal >= batteryChargeLevel(battery).ordinal) {
        batteryTempLevel(battery)
    } else {
        batteryChargeLevel(battery)
    }
    MetricCard(
        title = stringResource(R.string.metric_battery),
        fraction = (percent ?: 0) / 100f,
        centerText = if (percent != null) "$percent%" else "—",
        headline = headline,
        detail = battery.temperatureC?.let { stringResource(R.string.battery_temperature, formatTemperature(it)) }.orEmpty(),
        level = level
    )
}

@Composable
private fun RecommendationItem(rec: Recommendation, onOpenApps: (AppSort) -> Unit) {
    val context = LocalContext.current
    when (rec.type) {
        RecommendationType.STORAGE_LOW -> RecommendationCard(
            level = rec.level,
            title = stringResource(R.string.reco_storage_title),
            body = stringResource(R.string.reco_storage_body),
            actionLabel = stringResource(R.string.reco_storage_action),
            onAction = { context.openFirstAvailable(*storageIntents()) }
        )
        RecommendationType.RAM_LOW -> RecommendationCard(
            level = rec.level,
            title = stringResource(R.string.reco_ram_title),
            body = stringResource(R.string.reco_ram_body),
            actionLabel = stringResource(R.string.reco_ram_action),
            onAction = { onOpenApps(AppSort.SCREEN_TIME) }
        )
        RecommendationType.BATTERY_HOT -> RecommendationCard(
            level = rec.level,
            title = stringResource(R.string.reco_hot_title),
            body = stringResource(R.string.reco_hot_body),
            actionLabel = stringResource(R.string.reco_battery_action),
            onAction = { context.openFirstAvailable(*batteryIntents()) }
        )
        RecommendationType.BATTERY_LOW -> RecommendationCard(
            level = rec.level,
            title = stringResource(R.string.reco_low_title),
            body = stringResource(R.string.reco_low_body),
            actionLabel = stringResource(R.string.reco_battery_action),
            onAction = { context.openFirstAvailable(*batteryIntents()) }
        )
        RecommendationType.USAGE_ACCESS -> RecommendationCard(
            level = rec.level,
            title = stringResource(R.string.reco_access_title),
            body = stringResource(R.string.reco_access_body),
            actionLabel = stringResource(R.string.reco_access_action),
            onAction = { context.openFirstAvailable(*usageAccessIntents(context.packageName)) }
        )
        RecommendationType.UNUSED_APPS -> RecommendationCard(
            level = rec.level,
            title = context.resources.getQuantityString(R.plurals.reco_idle_title, rec.count, rec.count),
            body = stringResource(R.string.reco_idle_body),
            actionLabel = stringResource(R.string.reco_idle_action),
            onAction = { onOpenApps(AppSort.IDLE) }
        )
    }
}

@Composable
private fun ModesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeRow(stringResource(R.string.mode_noroot), active = true)
            ModeRow(stringResource(R.string.mode_shizuku), active = false)
            ModeRow(stringResource(R.string.mode_root), active = false)
            Text(
                stringResource(R.string.modes_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModeRow(label: String, active: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(if (active) R.string.mode_active else R.string.mode_planned),
            style = MaterialTheme.typography.labelLarge,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
