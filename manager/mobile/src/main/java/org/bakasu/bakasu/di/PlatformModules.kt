package org.bakasu.bakasu.di

import org.bakasu.bakasu.domain.model.ManagerDevice
import org.bakasu.bakasu.ui.activity.util.ThemeUtils
import org.bakasu.bakasu.ui.util.Shortcut
import org.bakasu.bakasu.ui.viewmodel.ScriptRunViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

private val platformCoreModule = module {
    single { ManagerDevice.MOBILE }
}

private val platformRepositoryModule = module {
    singleOf(::ThemeUtils)
    singleOf(::Shortcut)
}

private val platformViewModelModule = module {
    viewModel { parameters -> ScriptRunViewModel(parameters[0], get(), get(), get(), get(), get()) }
}

val platformModules = listOf(platformCoreModule, platformRepositoryModule, platformViewModelModule)
