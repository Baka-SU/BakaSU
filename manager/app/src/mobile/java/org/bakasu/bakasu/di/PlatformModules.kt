package org.bakasu.bakasu.di

import org.bakasu.bakasu.ui.activity.util.ThemeUtils
import org.bakasu.bakasu.ui.util.Shortcut
import org.bakasu.bakasu.ui.viewmodel.ScriptRunViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel

internal fun Module.platformRepositories() {
    singleOf(::ThemeUtils)
    singleOf(::Shortcut)
}

internal fun Module.platformUseCases() = Unit

internal fun Module.platformViewModels() {
    viewModel { parameters -> ScriptRunViewModel(parameters[0], get(), get(), get(), get(), get()) }
}
