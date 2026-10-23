package cloud.kvd.androidoptimizer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import cloud.kvd.androidoptimizer.R
import cloud.kvd.androidoptimizer.domain.AppEntry
import cloud.kvd.androidoptimizer.domain.AppSort
import cloud.kvd.androidoptimizer.domain.filterApps
import cloud.kvd.androidoptimizer.domain.idleDays
import cloud.kvd.androidoptimizer.domain.sortedFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppsScreen(
    state: UiState,
    sort: AppSort,
    onSortChange: (AppSort) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var includeSystem by rememberSaveable { mutableStateOf(false) }

    // Без Usage Access сортировки по давности и времени не имеют смысла.
    val effectiveSort = if (state.usageAccess) sort else AppSort.NAME
    val shown = remember(state.apps, query, includeSystem, effectiveSort) {
        filterApps(state.apps, query, includeSystem).sortedFor(effectiveSort)
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.apps_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortChip(AppSort.IDLE, R.string.sort_idle, effectiveSort, state.usageAccess, onSortChange)
            SortChip(AppSort.SCREEN_TIME, R.string.sort_screen_time, effectiveSort, state.usageAccess, onSortChange)
            SortChip(AppSort.NAME, R.string.sort_name, effectiveSort, true, onSortChange)
            FilterChip(
                selected = includeSystem,
                onClick = { includeSystem = !includeSystem },
                label = { Text(stringResource(R.string.filter_system)) }
            )
        }

        if (!state.usageAccess) {
            UsageBanner(
                onGrant = { context.openFirstAvailable(*usageAccessIntents(context.packageName)) },
                modifier = Modifier.padding(16.dp)
            )
        }

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
    val subtitle = if (!usageAccess) {
        app.packageName
    } else {
        val idle = app.idleDays(nowMs).toInt()
        val lastUse = when {
            app.lastUsedMs != null && idle == 0 -> stringResource(R.string.app_used_today)
            app.lastUsedMs == null && idle == 0 -> stringResource(R.string.app_not_used_yet)
            else -> context.resources.getQuantityString(R.plurals.app_idle_days, idle, idle)
        }
        lastUse + " · " + stringResource(R.string.app_screen_time_7d, formatDuration(context, app.foregroundMs7d))
    }

    ListItem(
        leadingContent = { AppIcon(app.packageName, Modifier.size(40.dp)) },
        headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
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

/** Иконка грузится лениво и вне главного потока, чтобы не тормозить прокрутку. */
@Composable
private fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(bitmap = loaded, contentDescription = null, modifier = modifier)
    } else {
        Spacer(modifier)
    }
}
