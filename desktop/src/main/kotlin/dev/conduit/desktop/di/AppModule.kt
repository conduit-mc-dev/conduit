package dev.conduit.desktop.di

import dev.conduit.core.api.ConduitApiClient
import dev.conduit.desktop.ui.instance.CreateInstanceViewModel
import dev.conduit.desktop.ui.instance.InstanceListViewModel
import dev.conduit.desktop.ui.pair.PairViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { ConduitApiClient("http://localhost:9147") }

    viewModel { PairViewModel(get()) }
    viewModel { InstanceListViewModel(get()) }
    viewModel { CreateInstanceViewModel(get()) }
}
