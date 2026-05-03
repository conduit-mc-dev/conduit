package dev.conduit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.conduit.core.api.ConduitApiClient
import dev.conduit.desktop.di.appModule
import dev.conduit.desktop.navigation.*
import dev.conduit.desktop.session.SessionManager
import dev.conduit.desktop.ui.components.InstanceListPanel
import dev.conduit.desktop.ui.components.Sidebar
import dev.conduit.desktop.ui.instance.CreateInstanceScreen
import dev.conduit.desktop.ui.instance.InstanceDetailScreen
import dev.conduit.desktop.ui.instance.InstanceListScreen
import dev.conduit.desktop.ui.instance.InstanceListViewModel
// ServerPropertiesScreen removed — route deleted in Routes v2
import dev.conduit.desktop.ui.pair.PairScreen
import dev.conduit.desktop.ui.theme.ConduitTheme
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.koinConfiguration
import org.koin.mp.KoinPlatformTools

fun main() {
    val savedSession = SessionManager.loadFromDisk()
    val isPaired = savedSession != null
    @Suppress("DEPRECATION")
    val appIcon = BitmapPainter(useResource("logo-icon.png") { loadImageBitmap(it) })
    @Suppress("DEPRECATION")
    val sidebarIcon = BitmapPainter(useResource("logo-icon-transparent.png") { loadImageBitmap(it) })
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Conduit MC",
            icon = appIcon,
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            // Enforce minimum window size via AWT
            val window = java.awt.Window.getWindows().firstOrNull()
            LaunchedEffect(Unit) {
                window?.minimumSize = java.awt.Dimension(900, 600)
            }

            KoinApplication(koinConfiguration { modules(appModule) }) {
                if (savedSession != null) {
                    val koin = KoinPlatformTools.defaultContext().get()
                    val apiClient: ConduitApiClient = koin.get()
                    val session: SessionManager = koin.get()
                    apiClient.setBaseUrl(savedSession.daemonUrl)
                    apiClient.setToken(savedSession.token)
                    session.start(savedSession.token)
                }

                ConduitTheme {
                    var currentMode by remember { mutableStateOf(AppMode.MANAGE) }
                    var selectedInstanceId by remember { mutableStateOf<String?>(null) }
                    val navController = rememberNavController()

                    Row(modifier = Modifier.fillMaxSize()) {
                        // Column 1: Icon rail
                        Sidebar(
                            currentMode = currentMode,
                            appIcon = sidebarIcon,
                            onModeChange = { newMode ->
                                currentMode = newMode
                                when (newMode) {
                                    AppMode.MANAGE -> {
                                        if (isPaired) {
                                            selectedInstanceId = null
                                            navController.navigate(InstanceListRoute) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    }
                                    AppMode.LAUNCHER -> {
                                        navController.navigate(LauncherRoute) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                    AppMode.SETTINGS -> {
                                        navController.navigate(SettingsRoute) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                }
                            },
                        )

                        // Instance list ViewModel (shared between sidebar panel and NavHost)
                        val instanceListVm: InstanceListViewModel = koinViewModel()
                        val instanceListState by instanceListVm.state.collectAsState()

                        // Column 2: Instance list panel (only in MANAGE mode when paired)
                        if (currentMode == AppMode.MANAGE && isPaired) {
                            InstanceListPanel(
                                instances = instanceListState.instances,
                                selectedInstanceId = selectedInstanceId,
                                isLoading = instanceListState.isLoading,
                                onInstanceClick = { id ->
                                    selectedInstanceId = id
                                    navController.navigate(InstanceDetailRoute(id, daemonId = "")) {
                                        // Pop previous detail if any, so clicking another instance replaces it
                                        popUpTo<InstanceListRoute> { inclusive = false }
                                    }
                                },
                                onCreateInstance = {
                                    navController.navigate(CreateInstanceRoute)
                                },
                                onRefresh = instanceListVm::refresh,
                            )
                        }

                        // Column 3: Main content
                        Surface(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            NavHost(
                                navController = navController,
                                startDestination = when {
                                    !isPaired -> PairRoute
                                    currentMode == AppMode.LAUNCHER -> LauncherRoute
                                    currentMode == AppMode.SETTINGS -> SettingsRoute
                                    else -> InstanceListRoute
                                },
                            ) {
                                composable<PairRoute> {
                                    PairScreen(
                                        onPaired = {
                                            navController.navigate(InstanceListRoute) {
                                                popUpTo(PairRoute) { inclusive = true }
                                            }
                                        },
                                    )
                                }
                                composable<InstanceListRoute> {
                                    InstanceListScreen(
                                        onCreateInstance = {
                                            navController.navigate(CreateInstanceRoute)
                                        },
                                        hasInstances = instanceListState.instances.isNotEmpty(),
                                    )
                                }
                                composable<CreateInstanceRoute> {
                                    CreateInstanceScreen(
                                        onCreated = {
                                            navController.popBackStack()
                                        },
                                        onCancel = {
                                            navController.popBackStack()
                                        },
                                    )
                                }
                                composable<InstanceDetailRoute> { backStackEntry ->
                                    val route = backStackEntry.toRoute<InstanceDetailRoute>()
                                    InstanceDetailScreen(
                                        instanceId = route.instanceId,
                                        onEditProperties = {
                                            // TODO: navigate to properties view (Task 19+)
                                        },
                                        onDeleted = {
                                            selectedInstanceId = null
                                            navController.navigate(InstanceListRoute) {
                                                popUpTo(InstanceDetailRoute(route.instanceId, route.daemonId)) { inclusive = true }
                                            }
                                        },
                                    )
                                }
                                // ServerPropertiesRoute removed in Routes v2 — properties tab coming in Task 19
                                composable<LauncherRoute> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "启动器 — 即将推出",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                composable<SettingsRoute> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "设置 — 即将推出",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
