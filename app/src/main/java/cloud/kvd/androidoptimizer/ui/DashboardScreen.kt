package cloud.kvd.androidoptimizer.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cloud.kvd.androidoptimizer.R
import cloud.kvd.androidoptimizer.domain.AppEntry
import cloud.kvd.androidoptimizer.domain.AppSort
import cloud.kvd.androidoptimizer.domain.DeviceSnapshot
import cloud.kvd.androidoptimizer.domain.Level
import cloud.kvd.androidoptimizer.domain.Recommendation
import cloud.kvd.androidoptimizer.domain.RecommendationType
import cloud.kvd.androidoptimizer.domain.batteryChargeLevel
import cloud.kvd.androidoptimizer.domain.batteryTempLevel
import cloud.kvd.androidoptimizer.domain.overallLevel
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "hero") { HeroCard(snapshot, state.recommendations) }
        item(key = "storage") { StorageCard(snapshot, state) }
        if (state.topApps.isNotEmpty()) {
            item(key = "top") { TopAppsCard(state.topApps, onSeeAll = { onOpenApps(AppSort.SIZE) }) }
        }

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
private fun HeroCard(snapshot: DeviceSnapshot, recommendations: List<Recommendation>) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val status = overallLevel(recommendations)
    val issues = recommendations.count { it.level == Level.WARNING || it.level == Level.CRITICAL }
    val onHero = scheme.onPrimaryContainer
    val track = onHero.copy(alpha = 0.15f)

    val ramUsed = (snapshot.ramUsedFraction * 100).roundToInt()
    val storageUsed = (snapshot.storageUsedFraction * 100).roundToInt()
    val battery = snapshot.battery
    val percent = battery.percent
    val batteryLevel = if (batteryTempLevel(battery).ordinal >= batteryChargeLevel(battery).ordinal) {
        batteryTempLevel(battery)
    } else {
        batteryChargeLevel(battery)
    }
    val batteryCaption = when {
        battery.isCharging -> stringResource(R.string.battery_charging)
        battery.temperatureC != null -> stringResource(R.string.temperature_short, formatTemperature(battery.temperatureC!!))
        percent == null -> stringResource(R.string.battery_unknown)
        else -> stringResource(R.string.battery_discharging)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerHigh)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(snapshot.displayName, style = MaterialTheme.typography.headlineSmall, color = onHero)
                    Text(
                        stringResource(R.string.device_android, snapshot.androidVersion, snapshot.sdkInt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onHero.copy(alpha = 0.7f)
                    )
                }
                StatusPill(
                    text = if (status == Level.NORMAL) stringResource(R.string.hero_ok) else stringResource(R.string.hero_issues, issues),
                    level = status
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroGauge(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.gauge_ram),
                    fraction = snapshot.ramUsedFraction,
                    centerText = "$ramUsed%",
                    caption = stringResource(R.string.gauge_free, Formatter.formatShortFileSize(context, snapshot.availableRamBytes)),
                    level = ramLevel(snapshot),
                    textColor = onHero,
                    trackColor = track
                )
                HeroGauge(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.metric_storage),
                    fraction = snapshot.storageUsedFraction,
                    centerText = "$storageUsed%",
                    caption = stringResource(R.string.gauge_free, Formatter.formatShortFileSize(context, snapshot.freeStorageBytes)),
                    level = storageLevel(snapshot),
                    textColor = onHero,
                    trackColor = track
                )
                HeroGauge(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.metric_battery),
                    fraction = (percent ?: 0) / 100f,
                    centerText = if (percent != null) "$percent%" else "—",
                    caption = batteryCaption,
                    level = batteryLevel,
                    textColor = onHero,
                    trackColor = track
                )
            }
        }
    }
}

@Composable
private fun HeroGauge(
    label: String,
    fraction: Float,
    centerText: String,
    caption: String,
    level: Level,
    textColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RingGauge(
            fraction = fraction,
            color = level.accentColor(),
            trackColor = trackColor,
            modifier = Modifier.size(88.dp)
        ) {
            Text(centerText, style = MaterialTheme.typography.titleMedium, color = textColor)
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = textColor)
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StorageCard(snapshot: DeviceSnapshot, state: UiState) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val level = storageLevel(snapshot)
    val usedBytes = (snapshot.totalStorageBytes - snapshot.freeStorageBytes).coerceAtLeast(0L)
    val breakdown = state.breakdown

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.metric_storage), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${(snapshot.storageUsedFraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = level.accentColor()
                )
            }
            Text(
                stringResource(
                    R.string.storage_used_of,
                    Formatter.formatFileSize(context, usedBytes),
                    Formatter.formatFileSize(context, snapshot.totalStorageBytes)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )

            if (breakdown != null) {
                SegmentedBar(
                    listOf(
                        breakdown.appsBytes.toFloat() to scheme.primary,
                        breakdown.cacheBytes.toFloat() to scheme.tertiary,
                        breakdown.otherBytes.toFloat() to scheme.secondary,
                        breakdown.freeBytes.toFloat() to scheme.surfaceVariant
                    )
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendRow(scheme.primary, stringResource(R.string.legend_apps), Formatter.formatFileSize(context, breakdown.appsBytes))
                    LegendRow(scheme.tertiary, stringResource(R.string.legend_cache), Formatter.formatFileSize(context, breakdown.cacheBytes))
                    LegendRow(scheme.secondary, stringResource(R.string.legend_other), Formatter.formatFileSize(context, breakdown.otherBytes))
                    LegendRow(scheme.surfaceVariant, stringResource(R.string.legend_free), Formatter.formatFileSize(context, breakdown.freeBytes))
                }
            } else {
                SegmentedBar(
                    listOf(
                        usedBytes.toFloat() to level.accentColor(),
                        snapshot.freeStorageBytes.toFloat() to scheme.surfaceVariant
                    )
                )
                val hint = when {
                    !state.usageAccess -> stringResource(R.string.storage_need_access)
                    state.sizesLoading -> stringResource(R.string.storage_calculating)
                    else -> null
                }
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }

            TextButton(
                onClick = { context.openFirstAvailable(*storageIntents()) },
                modifier = Modifier.align(Alignment.End)
            ) { Text(stringResource(R.string.reco_storage_action)) }
        }
    }
}

@Composable
private fun TopAppsCard(apps: List<AppEntry>, onSeeAll: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val maxSize = (apps.maxOfOrNull { it.size?.totalBytes ?: 0L } ?: 1L).coerceAtLeast(1L)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.section_top_apps), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onSeeAll) { Text(stringResource(R.string.action_see_all)) }
            }
            apps.forEach { app ->
                val bytes = app.size?.totalBytes ?: 0L
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(app.packageName, Modifier.size(36.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row {
                            Text(
                                app.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                Formatter.formatFileSize(context, bytes),
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { bytes.toFloat() / maxSize.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = scheme.primary,
                            trackColor = scheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
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
        RecommendationType.CACHE_LARGE -> RecommendationCard(
            level = rec.level,
            title = stringResource(R.string.reco_cache_title, Formatter.formatFileSize(context, rec.bytes)),
            body = stringResource(R.string.reco_cache_body),
            actionLabel = stringResource(R.string.reco_cache_action),
            onAction = { context.openFirstAvailable(*storageIntents()) }
        )
    }
}

@Composable
private fun ModesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
