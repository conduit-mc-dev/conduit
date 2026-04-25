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
import dev.conduit.desktop.di.appModule
import dev.conduit.desktop.navigation.CreateInstanceRoute
import dev.conduit.desktop.navigation.InstanceDetailRoute
import dev.conduit.desktop.navigation.InstanceListRoute
import dev.conduit.desktop.navigation.PairRoute
import dev.conduit.desktop.ui.instance.CreateInstanceScreen
import dev.conduit.desktop.ui.instance.InstanceDetailScreen
import dev.conduit.desktop.ui.instance.InstanceListScreen
import dev.conduit.desktop.ui.pair.PairScreen
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Conduit MC",
        state = rememberWindowState(width = 900.dp, height = 640.dp),
    ) {
        KoinApplication(koinConfiguration { modules(appModule) }) {
            MaterialTheme {
                Surface {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = PairRoute) {
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
                            )
                        }
                    }
                }
            }
        }
    }
}
