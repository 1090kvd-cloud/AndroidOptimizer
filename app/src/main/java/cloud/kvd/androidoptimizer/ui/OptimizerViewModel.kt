package cloud.kvd.androidoptimizer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cloud.kvd.androidoptimizer.data.AppUsageRepository
import cloud.kvd.androidoptimizer.data.DeviceDiagnostics
import cloud.kvd.androidoptimizer.domain.AppEntry
import cloud.kvd.androidoptimizer.domain.DeviceSnapshot
import cloud.kvd.androidoptimizer.domain.Recommendation
import cloud.kvd.androidoptimizer.domain.buildRecommendations
import cloud.kvd.androidoptimizer.domain.isIdle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val snapshot: DeviceSnapshot? = null,
    val usageAccess: Boolean = false,
    val apps: List<AppEntry> = emptyList(),
    val appsLoading: Boolean = true,
    val idleAppsCount: Int = 0,
    val recommendations: List<Recommendation> = emptyList(),
    val nowMs: Long = 0L
)

class OptimizerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = AppUsageRepository(appContext)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    /** Обновляет диагностику сразу, а список приложений — следом (он медленнее). */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            val (snapshot, access) = withContext(Dispatchers.IO) {
                DeviceDiagnostics.read(appContext) to repository.hasUsageAccess()
            }
            _state.update {
                it.copy(
                    snapshot = snapshot,
                    usageAccess = access,
                    appsLoading = true,
                    nowMs = now,
                    recommendations = buildRecommendations(snapshot, access, if (access) it.idleAppsCount else 0)
                )
            }

            val apps = withContext(Dispatchers.IO) { repository.loadApps(now) }
            // Без Usage Access «давность запуска» неизвестна — не считаем неиспользуемые приложения.
            val idle = if (access) apps.count { it.isIdle(now) } else 0
            _state.update {
                it.copy(
                    apps = apps,
                    appsLoading = false,
                    idleAppsCount = idle,
                    recommendations = buildRecommendations(snapshot, access, idle)
                )
            }
        }
    }
}
