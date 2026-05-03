package dev.conduit.desktop.di

import dev.conduit.desktop.session.DaemonManager
import dev.conduit.desktop.ui.daemon.DaemonViewModel
import dev.conduit.desktop.ui.instance.*
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { DaemonManager() }

    viewModel { InstanceListViewModel(get()) }
    viewModel { (daemonId: String) -> CreateInstanceViewModel(daemonId, get()) }
    viewModel { (instanceId: String, daemonId: String) -> InstanceDetailViewModel(instanceId, daemonId, get()) }
    viewModel { (instanceId: String, daemonId: String) -> ConfigTabViewModel(instanceId, daemonId, get()) }
    viewModel { (instanceId: String, daemonId: String) -> ModsTabViewModel(instanceId, daemonId, get()) }
    viewModel { (instanceId: String, daemonId: String) -> FilesTabViewModel(instanceId, daemonId, get()) }
    viewModel { DaemonViewModel(get()) }
}
