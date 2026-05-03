package dev.conduit.desktop.di

import dev.conduit.core.api.ConduitApiClient
import dev.conduit.desktop.session.DaemonManager
import dev.conduit.desktop.session.SessionManager
import dev.conduit.desktop.ui.instance.ConfigTabViewModel
import dev.conduit.desktop.ui.instance.CreateInstanceViewModel
import dev.conduit.desktop.ui.instance.FilesTabViewModel
import dev.conduit.desktop.ui.instance.InstanceDetailViewModel
import dev.conduit.desktop.ui.instance.InstanceListViewModel
import dev.conduit.desktop.ui.instance.ModsTabViewModel
import dev.conduit.desktop.ui.instance.ServerPropertiesViewModel
import dev.conduit.desktop.ui.pair.PairViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { ConduitApiClient("http://localhost:9147") }
    single { SessionManager(get()) }
    single { DaemonManager() }

    viewModel { PairViewModel(get(), get()) }
    viewModel { InstanceListViewModel(get(), get()) }
    viewModel { CreateInstanceViewModel(get()) }
    viewModel { (instanceId: String) ->
        ServerPropertiesViewModel(instanceId, get())
    }
    viewModel { (instanceId: String) ->
        InstanceDetailViewModel(instanceId, get(), get())
    }
    viewModel { (instanceId: String, daemonId: String) ->
        ConfigTabViewModel(instanceId, daemonId, get())
    }
    viewModel { (instanceId: String, daemonId: String) ->
        ModsTabViewModel(instanceId, daemonId, get())
    }
    viewModel { (instanceId: String, daemonId: String) ->
        FilesTabViewModel(instanceId, daemonId, get())
    }
}
