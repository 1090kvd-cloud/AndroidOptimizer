package cloud.kvd.androidoptimizer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cloud.kvd.androidoptimizer.data.AppUsageRepository
import cloud.kvd.androidoptimizer.R
import cloud.kvd.androidoptimizer.data.DeviceDiagnostics
import cloud.kvd.androidoptimizer.data.JournalStore
import cloud.kvd.androidoptimizer.data.SystemSettingsRepository
import cloud.kvd.androidoptimizer.domain.AppEntry
import cloud.kvd.androidoptimizer.domain.DeviceSnapshot
import cloud.kvd.androidoptimizer.domain.JournalEntry
import cloud.kvd.androidoptimizer.domain.ProfileId
import cloud.kvd.androidoptimizer.domain.Recommendation
import cloud.kvd.androidoptimizer.domain.SettingsValues
import cloud.kvd.androidoptimizer.domain.buildRecommendations
import cloud.kvd.androidoptimizer.domain.diff
import cloud.kvd.androidoptimizer.domain.isIdle
import cloud.kvd.androidoptimizer.domain.latestRevertable
import cloud.kvd.androidoptimizer.domain.markReverted
import cloud.kvd.androidoptimizer.domain.profileValues
import cloud.kvd.androidoptimizer.domain.withNew
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val nowMs: Long = 0L,
    val canWriteSettings: Boolean = false,
    val currentSettings: SettingsValues = SettingsValues(),
    val journal: List<JournalEntry> = emptyList()
)

private class QuickData(
    val snapshot: DeviceSnapshot,
    val access: Boolean,
    val canWrite: Boolean,
    val settings: SettingsValues,
    val journal: List<JournalEntry>
)

class OptimizerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = AppUsageRepository(appContext)
    private val settingsRepository = SystemSettingsRepository(appContext)
    private val journalStore = JournalStore(appContext)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Одноразовые сообщения для пользователя (id строкового ресурса). */
    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val events: SharedFlow<Int> = _events.asSharedFlow()

    private var refreshJob: Job? = null

    /** Обновляет диагностику сразу, а список приложений — следом (он медленнее). */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            val quick = withContext(Dispatchers.IO) {
                QuickData(
                    snapshot = DeviceDiagnostics.read(appContext),
                    access = repository.hasUsageAccess(),
                    canWrite = settingsRepository.canWrite(),
                    settings = settingsRepository.read(),
                    journal = journalStore.load()
                )
            }
            val snapshot = quick.snapshot
            val access = quick.access
            _state.update {
                it.copy(
                    snapshot = snapshot,
                    usageAccess = access,
                    canWriteSettings = quick.canWrite,
                    currentSettings = quick.settings,
                    journal = quick.journal,
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
    /** Применяет профиль, записывая снимок «до» в журнал, чтобы изменение можно было отменить. */
    fun applyProfile(id: ProfileId) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                val before = settingsRepository.read()
                val target = profileValues(id)
                if (diff(before, target).isEmpty()) return@withContext R.string.toast_profile_already
                if (!settingsRepository.apply(target)) return@withContext R.string.toast_profile_failed
                val entry = JournalEntry(System.currentTimeMillis(), id, before, before.overlay(target))
                journalStore.save(journalStore.load().withNew(entry))
                R.string.toast_profile_applied
            }
            _events.tryEmit(message)
            reloadSettings()
        }
    }

    /** Возвращает настройки к состоянию «до» — только для самой свежей неотменённой записи. */
    fun revert(entryId: Long) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                val journal = journalStore.load()
                val entry = journal.latestRevertable()
                if (entry == null || entry.id != entryId) return@withContext R.string.toast_revert_failed
                if (!settingsRepository.apply(entry.before)) return@withContext R.string.toast_revert_failed
                journalStore.save(journal.markReverted(entry.id))
                R.string.toast_reverted
            }
            _events.tryEmit(message)
            reloadSettings()
        }
    }

    private suspend fun reloadSettings() {
        val (canWrite, settings, journal) = withContext(Dispatchers.IO) {
            Triple(settingsRepository.canWrite(), settingsRepository.read(), journalStore.load())
        }
        _state.update { it.copy(canWriteSettings = canWrite, currentSettings = settings, journal = journal) }
    }
}
