package cloud.kvd.androidoptimizer.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cloud.kvd.androidoptimizer.R
import cloud.kvd.androidoptimizer.domain.AppEntry
import cloud.kvd.androidoptimizer.domain.AppScope
import cloud.kvd.androidoptimizer.domain.AppSort
import cloud.kvd.androidoptimizer.domain.filterApps
import cloud.kvd.androidoptimizer.domain.idleDays
import cloud.kvd.androidoptimizer.domain.isIdle
import cloud.kvd.androidoptimizer.domain.sortedFor

@Composable
fun AppsScreen(
    state: UiState,
    sort: AppSort,
    onSortChange: (AppSort) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf(AppScope.USER) }

    // Без Usage Access сортировки по давности, времени и размеру не имеют смысла.
    val effectiveSort = if (state.usageAccess) sort else AppSort.NAME
    val shown = remember(state.apps, query, scope, effectiveSort) {
        filterApps(state.apps, query, scope).sortedFor(effectiveSort)
    }
    val knownBytes = shown.sumOf { it.size?.totalBytes ?: 0L }
    val summary = if (state.usageAccess && knownBytes > 0L) {
        stringResource(R.string.apps_summary_size, shown.size, Formatter.formatFileSize(context, knownBytes))
    } else {
        stringResource(R.string.apps_summary, shown.size)
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            placeholder = { Text(stringResource(R.string.apps_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScopeChip(AppScope.USER, R.string.scope_user, scope) { scope = it }
            ScopeChip(AppScope.SYSTEM, R.string.scope_system, scope) { scope = it }
            ScopeChip(AppScope.ALL, R.string.scope_all, scope) { scope = it }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortChip(AppSort.IDLE, R.string.sort_idle, effectiveSort, state.usageAccess, onSortChange)
            SortChip(AppSort.SIZE, R.string.sort_size, effectiveSort, state.usageAccess, onSortChange)
            SortChip(AppSort.SCREEN_TIME, R.string.sort_screen_time, effectiveSort, state.usageAccess, onSortChange)
            SortChip(AppSort.NAME, R.string.sort_name, effectiveSort, true, onSortChange)
        }

        if (!state.usageAccess) {
            UsageBanner(
                onGrant = { context.openFirstAvailable(*usageAccessIntents(context.packageName)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Text(
            summary,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        when {
            state.appsLoading && state.apps.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            shown.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.apps_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(shown, key = { it.packageName }) { app ->
                    AppRow(app = app, usageAccess = state.usageAccess, nowMs = state.nowMs)
                }
            }
        }
    }
}

@Composable
private fun ScopeChip(target: AppScope, labelRes: Int, current: AppScope, onSelect: (AppScope) -> Unit) {
    FilterChip(
        selected = current == target,
        onClick = { onSelect(target) },
        label = { Text(stringResource(labelRes)) }
    )
}

@Composable
private fun SortChip(
    target: AppSort,
    labelRes: Int,
    current: AppSort,
    enabled: Boolean,
    onSortChange: (AppSort) -> Unit
) {
    FilterChip(
        selected = current == target,
        onClick = { onSortChange(target) },
        enabled = enabled,
        label = { Text(stringResource(labelRes)) }
    )
}

@Composable
private fun UsageBanner(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.apps_usage_banner_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.apps_usage_banner_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onGrant, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_grant))
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, usageAccess: Boolean, nowMs: Long) {
    val context = LocalContext.current

    val parts = mutableListOf<String>()
    if (usageAccess) {
        app.size?.let { parts += Formatter.formatFileSize(context, it.totalBytes) }
        val idle = app.idleDays(nowMs).toInt()
        parts += when {
            app.lastUsedMs != null && idle == 0 -> context.getString(R.string.app_used_today)
            app.lastUsedMs == null && idle == 0 -> context.getString(R.string.app_not_used_yet)
            else -> context.resources.getQuantityString(R.plurals.app_idle_days, idle, idle)
        }
        parts += context.getString(R.string.app_screen_time_7d, formatDuration(context, app.foregroundMs7d))
    } else {
        parts += app.packageName
    }
    val subtitle = parts.joinToString(" · ")

    // Пары «текст — выделить ли»: «Не используется» подсвечивается, остальные нейтральны.
    val badges = mutableListOf<Pair<String, Boolean>>()
    if (app.isSystem) badges += context.getString(R.string.badge_system) to false
    if (!app.isEnabled) badges += context.getString(R.string.badge_disabled) to false
    if (!app.hasLauncher) badges += context.getString(R.string.badge_no_launcher) to false
    if (usageAccess && app.isIdle(nowMs)) badges += context.getString(R.string.badge_idle) to true

    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(app.packageName, Modifier.size(36.dp))
            }
        },
        headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (badges.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        badges.forEach { (text, highlight) -> Badge(text, highlight = highlight) }
                    }
                }
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = { context.openFirstAvailable(*appDetailsIntents(app.packageName)) }) {
                    Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.cd_app_info))
                }
                if (!app.isSystem) {
                    IconButton(onClick = { context.openFirstAvailable(*uninstallIntents(app.packageName)) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_app_uninstall))
                    }
                }
            }
        }
    )
}
