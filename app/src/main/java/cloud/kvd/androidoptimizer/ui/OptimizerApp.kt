package cloud.kvd.androidoptimizer.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cloud.kvd.androidoptimizer.R
import cloud.kvd.androidoptimizer.domain.AppSort

private enum class AppTab(val labelRes: Int, val icon: ImageVector) {
    OVERVIEW(R.string.tab_overview, Icons.Filled.Home),
    APPS(R.string.tab_apps, Icons.AutoMirrored.Filled.List)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimizerApp(viewModel: OptimizerViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Возврат из системных настроек (например, после выдачи Usage Access) обновляет данные сам.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var appsSort by rememberSaveable { mutableStateOf(AppSort.IDLE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        Crossfade(targetState = tabIndex, label = "tabs") { index ->
            when (AppTab.entries[index]) {
                AppTab.OVERVIEW -> DashboardScreen(
                    state = state,
                    onOpenApps = { sort ->
                        appsSort = sort
                        tabIndex = AppTab.APPS.ordinal
                    },
                    modifier = Modifier.padding(padding)
                )
                AppTab.APPS -> AppsScreen(
                    state = state,
                    sort = appsSort,
                    onSortChange = { appsSort = it },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}
