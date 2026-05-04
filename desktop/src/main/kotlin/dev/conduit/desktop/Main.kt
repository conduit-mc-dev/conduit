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
import dev.conduit.desktop.di.appModule
import dev.conduit.desktop.navigation.*
import dev.conduit.desktop.session.DaemonManager
import dev.conduit.desktop.ui.components.*
import dev.conduit.core.model.WsConnectionState
import dev.conduit.desktop.ui.daemon.DaemonViewModel
import dev.conduit.desktop.ui.instance.*
import dev.conduit.desktop.ui.launch.LaunchEmptyScreen
import dev.conduit.desktop.ui.launch.PairedEmptyScreen
import dev.conduit.desktop.ui.pair.DaemonForm
import dev.conduit.desktop.ui.theme.ConduitTheme
import dev.conduit.desktop.ui.theme.TextSecondary
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinConfiguration
import org.koin.mp.KoinPlatformTools

fun main() {
    @Suppress("DEPRECATION")
    val appIcon = BitmapPainter(useResource("logo-icon.png") { loadImageBitmap(it) })

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Conduit MC",
            icon = appIcon,
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            val window = java.awt.Window.getWindows().firstOrNull()
            LaunchedEffect(Unit) { window?.minimumSize = java.awt.Dimension(900, 600) }

            KoinApplication(koinConfiguration { modules(appModule) }) {
                val koin = KoinPlatformTools.defaultContext().get()
                val daemonManager: DaemonManager = koin.get()

                val savedSession = daemonManager.loadSavedSession()
                var currentDaemonId by remember { mutableStateOf(savedSession?.daemonId ?: "") }
                val isPaired = savedSession != null

                LaunchedEffect(Unit) {
                    if (savedSession != null && daemonManager.getSession(savedSession.daemonId) == null) {
                        daemonManager.addDaemon(
                            savedSession.daemonId,
                            savedSession.daemonName,
                            savedSession.daemonUrl,
                            savedSession.token,
                        )
                    }
                }

                ConduitTheme {
                    var currentMode by remember { mutableStateOf(AppMode.MANAGE) }
                    var selectedInstanceId by remember { mutableStateOf<String?>(null) }
                    val navController = rememberNavController()

                    Row(modifier = Modifier.fillMaxSize()) {
                        // Column 1: NavigationRail (always visible)
                        NavigationRail(
                            currentMode = currentMode,
                            onModeChange = { newMode ->
                                currentMode = newMode
                                when (newMode) {
                                    AppMode.LAUNCHER -> {
                                        navController.navigate(LauncherRoute) {
                                            popUpTo<LauncherRoute> { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                    AppMode.MANAGE -> {
                                        val target = if (isPaired) InstanceListRoute else PairRoute
                                        if (!navController.popBackStack(target::class, false)) {
                                            navController.navigate(target) { launchSingleTop = true }
                                        }
                                    }
                                    AppMode.SETTINGS -> {
                                        navController.navigate(SettingsRoute) {
                                            popUpTo<SettingsRoute> { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            },
                        )

                        // Column 2: Instance list panel (always visible in MANAGE mode)
                        if (currentMode == AppMode.MANAGE) {
                            val listVm: InstanceListViewModel = koinViewModel()
                            val listState by listVm.state.collectAsState()
                            val daemonVm: DaemonViewModel = koinViewModel()
                            var forgetTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
                            forgetTarget?.let { (id, name) ->
                                ForgetDaemonDialog(
                                    daemonName = name,
                                    daemonAddress = daemonManager.getSession(id)?.daemonUrl ?: "",
                                    serverCount = listState.daemonGroups.find { it.daemonId == id }?.instances?.size ?: 0,
                                    onConfirm = { daemonVm.forget(id); forgetTarget = null },
                                    onDismiss = { forgetTarget = null },
                                )
                            }

                            InstanceListPanel(
                                daemonGroups = listState.daemonGroups,
                                selectedInstanceId = selectedInstanceId,
                                onInstanceClick = { daemonId, instanceId ->
                                    selectedInstanceId = instanceId
                                    currentDaemonId = daemonId
                                    navController.navigate(
                                        InstanceDetailRoute(instanceId, daemonId),
                                    ) {
                                        popUpTo<InstanceListRoute> { inclusive = false }
                                    }
                                },
                                onCreateInstance = { daemonId ->
                                    navController.navigate(CreateInstanceRoute(daemonId))
                                },
                                onPairDaemon = {
                                    navController.navigate(PairRoute) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onDaemonEdit = { daemonId ->
                                    navController.navigate(DaemonEditRoute(daemonId))
                                },
                                onDaemonForget = { daemonId, daemonName ->
                                    forgetTarget = daemonId to daemonName
                                },
                            )
                        }

                        // Column 3: Content
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
                                    DaemonForm(
                                        daemonId = null,
                                        onDone = { daemonId ->
                                            currentDaemonId = daemonId
                                            currentMode = AppMode.MANAGE
                                            navController.navigate(InstanceListRoute) {
                                                popUpTo(PairRoute) { inclusive = true }
                                            }
                                        },
                                    )
                                }
                                composable<InstanceListRoute> {
                                    val listVm: InstanceListViewModel = koinViewModel()
                                    val listState by listVm.state.collectAsState()
                                    val allInstances = listState.daemonGroups.flatMap { g ->
                                        g.instances.map { g.daemonId to it }
                                    }

                                    LaunchedEffect(allInstances, selectedInstanceId) {
                                        if (allInstances.isNotEmpty()) {
                                            val match = allInstances.find { it.second.id == selectedInstanceId }
                                            val (daemonId, instance) = match ?: allInstances.first()
                                            selectedInstanceId = instance.id
                                            currentDaemonId = daemonId
                                            navController.navigate(InstanceDetailRoute(instance.id, daemonId)) {
                                                popUpTo<InstanceListRoute> { inclusive = false }
                                            }
                                        }
                                    }

                                    if (allInstances.isEmpty()) {
                                        PairedEmptyScreen(
                                            onCreateServer = {
                                                navController.navigate(CreateInstanceRoute(currentDaemonId))
                                            },
                                        )
                                    }
                                }
                                composable<CreateInstanceRoute> { backStackEntry ->
                                    val route = backStackEntry.toRoute<CreateInstanceRoute>()
                                    CreateInstanceScreen(
                                        daemonId = route.daemonId,
                                        onCreated = { navController.popBackStack() },
                                        onCancel = { navController.popBackStack() },
                                    )
                                }
                                composable<InstanceDetailRoute> { backStackEntry ->
                                    val route = backStackEntry.toRoute<InstanceDetailRoute>()
                                    val detailVm: InstanceDetailViewModel = koinViewModel {
                                        parametersOf(route.instanceId, route.daemonId)
                                    }
                                    val detailState by detailVm.state.collectAsState()

                                    val session = daemonManager.getSession(route.daemonId)
                                    val daemonConnState = session?.connectionState?.collectAsState()?.value ?: WsConnectionState.DISCONNECTED
                                    val daemonDisplayName = session?.daemonName ?: route.daemonId

                                    InstanceDetailTabScreen(
                                        instanceId = route.instanceId,
                                        daemonId = route.daemonId,
                                        state = detailState,
                                        connectionState = daemonConnState,
                                        daemonName = daemonDisplayName,
                                        onSelectTab = detailVm::selectTab,
                                        onStart = detailVm::startServer,
                                        onStop = detailVm::stopServer,
                                        onRestart = detailVm::restartServer,
                                        onKill = detailVm::killServer,
                                        onDelete = { detailVm.setShowDeleteDialog(true) },
                                        onCancel = detailVm::cancelTask,
                                        onDismissDelete = { detailVm.setShowDeleteDialog(false) },
                                        onConfirmDelete = detailVm::deleteInstance,
                                        onEditDaemon = { navController.navigate(DaemonEditRoute(route.daemonId)) },
                                        onUpdateCommand = detailVm::updateCommandInput,
                                        onSendCommand = detailVm::sendCommand,
                                        onConfigDirtyChanged = { isDirty -> detailVm.setHasUnsavedConfig(isDirty) },
                                    )
                                }
                                composable<DaemonEditRoute> { backStackEntry ->
                                    val route = backStackEntry.toRoute<DaemonEditRoute>()
                                    DaemonForm(
                                        daemonId = route.daemonId,
                                        onDone = { navController.popBackStack() },
                                        onBack = { navController.popBackStack() },
                                    )
                                }
                                composable<LauncherRoute> {
                                    LaunchEmptyScreen(
                                        onCreateInstance = {
                                            navController.navigate(
                                                CreateInstanceRoute(currentDaemonId),
                                            )
                                        },
                                        onConnectServer = {
                                            navController.navigate(PairRoute) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        },
                                    )
                                }
                                composable<SettingsRoute> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Settings — coming soon",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = TextSecondary,
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
