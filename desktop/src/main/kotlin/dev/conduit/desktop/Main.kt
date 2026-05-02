package dev.conduit.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import dev.conduit.desktop.navigation.CreateInstanceRoute
import dev.conduit.desktop.navigation.InstanceDetailRoute
import dev.conduit.desktop.navigation.InstanceListRoute
import dev.conduit.desktop.navigation.PairRoute
import dev.conduit.desktop.navigation.ServerPropertiesRoute
import dev.conduit.desktop.session.SessionManager
import dev.conduit.desktop.ui.instance.CreateInstanceScreen
import dev.conduit.desktop.ui.instance.InstanceDetailScreen
import dev.conduit.desktop.ui.instance.InstanceListScreen
import dev.conduit.desktop.ui.instance.ServerPropertiesScreen
import dev.conduit.desktop.ui.pair.PairScreen
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import org.koin.mp.KoinPlatformTools

fun main() {
    val savedSession = SessionManager.loadFromDisk()
    val startDestination = if (savedSession != null) InstanceListRoute else PairRoute
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Conduit MC",
            state = rememberWindowState(width = 900.dp, height = 640.dp),
        ) {
            KoinApplication(koinConfiguration { modules(appModule) }) {
                // Initialize session synchronously before any composable that needs it
                if (savedSession != null) {
                    val koin = KoinPlatformTools.defaultContext().get()
                    val apiClient: ConduitApiClient = koin.get()
                    val session: SessionManager = koin.get()
                    apiClient.setBaseUrl(savedSession.daemonUrl)
                    apiClient.setToken(savedSession.token)
                    session.start(savedSession.token)
                }
                MaterialTheme {
                    Surface {
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = startDestination) {
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
                                    onInstanceClick = { instanceId ->
                                        navController.navigate(InstanceDetailRoute(instanceId))
                                    },
                                )
                            }
                            composable<CreateInstanceRoute> {
                                CreateInstanceScreen(
                                    onCreated = { navController.popBackStack() },
                                    onCancel = { navController.popBackStack() },
                                )
                            }
                            composable<InstanceDetailRoute> { backStackEntry ->
                                val route = backStackEntry.toRoute<InstanceDetailRoute>()
                                InstanceDetailScreen(
                                    instanceId = route.instanceId,
                                    onBack = { navController.popBackStack() },
                                    onEditProperties = {
                                        navController.navigate(ServerPropertiesRoute(route.instanceId))
                                    },
                                )
                            }
                            composable<ServerPropertiesRoute> { backStackEntry ->
                                val route = backStackEntry.toRoute<ServerPropertiesRoute>()
                                ServerPropertiesScreen(
                                    instanceId = route.instanceId,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
