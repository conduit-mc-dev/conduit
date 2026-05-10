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
import dev.conduit.core.model.WsMessage
import dev.conduit.core.model.TaskCompletedPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.Json
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
                val toastManager: ToastManager = koin.get()

                val savedSession = daemonManager.loadSavedSession()
                var currentDaemonId by remember { mutableStateOf(savedSession?.daemonId ?: "") }
                val sessions by daemonManager.sessions.collectAsState()
                val isPaired = savedSession != null || sessions.isNotEmpty()

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
                    var hasUnsavedConfig by remember { mutableStateOf(false) }
                    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }

                    fun navigateSafely(block: () -> Unit) {
                        if (hasUnsavedConfig) {
                            pendingNavigation = block
                        } else {
                            block()
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxSize()) {
                        // Column 1: NavigationRail (always visible)
                        NavigationRail(
                            currentMode = currentMode,
                            onModeChange = { newMode ->
                                currentMode = newMode
                                when (newMode) {
                                    AppMode.LAUNCHER -> {
                                        navigateSafely {
                                            navController.navigate(LauncherRoute) {
                                                popUpTo<LauncherRoute> { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                    AppMode.MANAGE -> {
                                        val target = if (isPaired) InstanceListRoute else PairRoute
                                        if (!navController.popBackStack(target::class, false)) {
                                            navController.navigate(target) { launchSingleTop = true }
                                        }
                                    }
                                    AppMode.SETTINGS -> {
                                        navigateSafely {
                                            navController.navigate(SettingsRoute) {
                                                popUpTo<SettingsRoute> { inclusive = false }
                                                launchSingleTop = true
                                            }
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
                                    onConfirm = {
                                        daemonVm.forget(id)
                                        val remaining = daemonManager.sessions.value
                                        if (remaining.isNotEmpty()) {
                                            currentDaemonId = remaining.first().daemonId
                                            selectedInstanceId = null
                                            navController.navigate(InstanceListRoute) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        } else {
                                            currentDaemonId = ""
                                            selectedInstanceId = null
                                            navController.navigate(PairRoute) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                        forgetTarget = null
                                    },
                                    onDismiss = { forgetTarget = null },
                                )
                            }

                            InstanceListPanel(
                                daemonGroups = listState.daemonGroups,
                                selectedInstanceId = selectedInstanceId,
                                onInstanceClick = { daemonId, instanceId ->
                                    navigateSafely {
                                        selectedInstanceId = instanceId
                                        currentDaemonId = daemonId
                                        navController.navigate(
                                            InstanceDetailRoute(instanceId, daemonId),
                                        ) {
                                            popUpTo<InstanceListRoute> { inclusive = false }
                                        }
                                    }
                                },
                                onCreateInstance = { daemonId ->
                                    navigateSafely {
                                        navController.navigate(CreateInstanceRoute(daemonId))
                                    }
                                },
                                onPairDaemon = {
                                    navigateSafely {
                                        navController.navigate(PairRoute) {
                                            popUpTo(0) { inclusive = true }
                                        }
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
                                        onConfigDirtyChanged = { isDirty ->
                                            detailVm.setHasUnsavedConfig(isDirty)
                                            hasUnsavedConfig = isDirty
                                        },
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

                        val toasts by toastManager.toasts.collectAsState()
                        ToastHost(toasts = toasts, onDismiss = toastManager::dismiss)

                        pendingNavigation?.let { nav ->
                            UnsavedChangesDialog(
                                changes = emptyList(),
                                onDiscard = {
                                    hasUnsavedConfig = false
                                    pendingNavigation = null
                                    nav()
                                },
                                onCancel = { pendingNavigation = null },
                                onSaveAndLeave = {
                                    hasUnsavedConfig = false
                                    pendingNavigation = null
                                    nav()
                                },
                            )
                        }

                        @OptIn(ExperimentalCoroutinesApi::class)
                        LaunchedEffect(Unit) {
                            val json = Json { ignoreUnknownKeys = true }
                            daemonManager.sessions.flatMapLatest { sessions ->
                                val flows = sessions.map { it.wsClient.messages }
                                if (flows.isEmpty()) emptyFlow()
                                else merge(*flows.toTypedArray())
                            }.collect { msg ->
                                if (msg.type == WsMessage.TASK_COMPLETED) {
                                    try {
                                        val payload = json.decodeFromJsonElement(TaskCompletedPayload.serializer(), msg.payload)
                                        toastManager.show(
                                            if (payload.success) ToastType.Success else ToastType.Error,
                                            payload.message,
                                        )
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
