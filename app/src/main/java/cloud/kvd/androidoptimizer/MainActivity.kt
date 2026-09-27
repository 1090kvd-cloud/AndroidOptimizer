package cloud.kvd.androidoptimizer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK))
        setContent { OptimizerTheme { OptimizerScreen() } }
    }
}

@Composable
private fun OptimizerScreen() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var section by rememberSaveable { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<DeviceSnapshot?>(null) }
    var apps by remember { mutableStateOf<List<AppUsageInfo>>(emptyList()) }
    var findings by remember { mutableStateOf<List<SystemAppFinding>>(emptyList()) }
    var journal by remember { mutableStateOf<List<ChangeRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var changingPackage by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var appError by remember { mutableStateOf<String?>(null) }
    var privacyError by remember { mutableStateOf<String?>(null) }
    var usageGranted by remember { mutableStateOf(false) }
    var shizuku by remember { mutableStateOf(ShizukuAccess.state()) }
    var before by remember { mutableStateOf<OptimizationSnapshot?>(null) }
    var comparison by remember { mutableStateOf<SnapshotComparison?>(null) }
    val notify: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) }; Unit }
    val open: (Intent) -> Unit = { intent ->
        try { context.startActivity(intent) }
        catch (_: Exception) { notify("Этот экран недоступен на устройстве. Откройте системные настройки вручную.") }
    }
    val appDetails: (String) -> Unit = { pkg ->
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
            ChangeJournal.add(context, "Открыты настройки: $pkg")
            journal = ChangeJournal.read(context)
        } catch (_: Exception) { notify("Не удалось открыть настройки приложения.") }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        val received = Shizuku.OnBinderReceivedListener { shizuku = ShizukuAccess.state() }
        val dead = Shizuku.OnBinderDeadListener { shizuku = ShizukuAccess.state() }
        val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> shizuku = ShizukuAccess.state() }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
        }
    }
    LaunchedEffect(revision) {
        loading = true
        appError = null
        shizuku = ShizukuAccess.state()
        val diagnostics = withContext(Dispatchers.IO) { runCatching { DeviceDiagnostics.read(context) } }
        diagnostics.onSuccess { snapshot = it }.onFailure { notify("Не удалось обновить показатели устройства.") }
        usageGranted = withContext(Dispatchers.IO) { AppAnalyzer.hasUsageAccess(context) }
        val inventory = withContext(Dispatchers.IO) { runCatching { AppAnalyzer.recentApps(context) } }
        inventory.onSuccess { apps = it }.onFailure { appError = "Не удалось загрузить приложения. Повторите анализ." }
        journal = withContext(Dispatchers.IO) { ChangeJournal.read(context) }
        loading = false
    }
    LaunchedEffect(section, revision) {
        if (section == 2) {
            scanning = true
            privacyError = null
            val result = withContext(Dispatchers.IO) { runCatching { PrivacyDebloatScanner.scan(context) } }
            result.onSuccess { findings = it }.onFailure { privacyError = "Не удалось проверить системные приложения. Попробуйте ещё раз." }
            scanning = false
        }
    }
    BackHandler(enabled = section != 0) { section = 0 }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (section != 0) {
                IconButton(onClick = { section = 0 }, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад")
                }
            }
            Box(Modifier.weight(1f)) {
            when (section) {
                0 -> DashboardScreen(snapshot, apps.size, loading, onRefresh = { revision++ },
                    onApps = { section = 1 }, onPrivacy = { section = 2 }, onAccess = { section = 3 },
                    onStorage = { section = 4 },
                    onBattery = { open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) })
                4 -> StorageScreen(snapshot,
                    onStorage = { open(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) },
                    onFiles = { open(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)) })
                1 -> ApplicationsScreen(apps, loading, appError, usageGranted,
                    onRefresh = { revision++ }, onUsageAccess = { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    onApp = appDetails)
                2 -> PrivacyScreen(findings, scanning, privacyError, onRefresh = { revision++ }, onApp = appDetails,
                    shizuku = shizuku, changingPackage = changingPackage, onSetup = { section = 3 },
                    onChange = { pkg, enabled ->
                        if (changingPackage == null) {
                            changingPackage = pkg
                            scope.launch {
                                try {
                                    val result = PackageControl.change(context.applicationContext, pkg, enabled)
                                    notify(result.message)
                                } finally {
                                    changingPackage = null
                                    revision++
                                }
                            }
                        }
                    })
                3 -> MoreScreen(shizuku, usageGranted, journal, before, comparison,
                    onOpen = open, onRefresh = { revision++ },
                    onPermission = {
                        runCatching { ShizukuAccess.requestPermission() }
                            .onFailure { notify("Не удалось запросить доступ. Проверьте, запущен ли Shizuku.") }
                    },
                    onBefore = { before = SnapshotTracker.capture(context); comparison = null },
                    onAfter = { before?.let { comparison = SnapshotTracker.compare(it, SnapshotTracker.capture(context)) } },
                    onClearJournal = { ChangeJournal.clear(context); journal = emptyList() })
            }
            }
        }
    }
}
