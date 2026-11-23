package org.bakasu.bakasu.di

import org.bakasu.bakasu.data.file.WearFileRepository
import org.bakasu.bakasu.data.network.WearLinkRepository
import org.bakasu.bakasu.domain.usecase.OpenWearExternalLinkUseCase
import org.bakasu.bakasu.ui.viewmodel.ModuleChangelogViewModel
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadViewModel
import org.bakasu.bakasu.ui.viewmodel.OnlineTemplateViewModel
import org.bakasu.bakasu.ui.viewmodel.WearDownloadsViewModel
import org.bakasu.bakasu.ui.viewmodel.WearFileViewModel
import org.bakasu.bakasu.ui.viewmodel.WearLinkViewModel
import org.bakasu.bakasu.ui.viewmodel.WearPreferencesViewModel
import org.bakasu.bakasu.ui.viewmodel.WearSearchViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf

internal fun Module.platformRepositories() {
    singleOf(::WearFileRepository)
    singleOf(::WearLinkRepository)
}

internal fun Module.platformUseCases() {
    factoryOf(::OpenWearExternalLinkUseCase)
}

internal fun Module.platformViewModels() {
    viewModelOf(::WearDownloadsViewModel)
    viewModelOf(::WearFileViewModel)
    viewModelOf(::WearLinkViewModel)
    viewModelOf(::WearPreferencesViewModel)
    viewModelOf(::OnlineTemplateViewModel)
    viewModelOf(::ModuleDownloadViewModel)
    viewModel { parameters -> WearSearchViewModel(parameters[0], get(), get()) }
    viewModel { parameters -> ModuleChangelogViewModel(parameters.get(), get()) }
}
