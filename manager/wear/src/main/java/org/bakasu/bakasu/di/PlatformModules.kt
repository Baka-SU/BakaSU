package org.bakasu.bakasu.di

import org.bakasu.bakasu.data.file.WearFileRepository
import org.bakasu.bakasu.data.network.WearLinkRepository
import org.bakasu.bakasu.domain.model.ManagerDevice
import org.bakasu.bakasu.domain.usecase.FindModuleUpdateSourceUseCase
import org.bakasu.bakasu.domain.usecase.OpenWearExternalLinkUseCase
import org.bakasu.bakasu.domain.usecase.ResolveInstalledModuleIconUseCase
import org.bakasu.bakasu.ui.viewmodel.ModuleChangelogViewModel
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadViewModel
import org.bakasu.bakasu.ui.viewmodel.WearDownloadsViewModel
import org.bakasu.bakasu.ui.viewmodel.WearFileViewModel
import org.bakasu.bakasu.ui.viewmodel.WearLinkViewModel
import org.bakasu.bakasu.ui.viewmodel.WearPreferencesViewModel
import org.bakasu.bakasu.ui.viewmodel.WearSearchViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

private val platformCoreModule = module {
    single { ManagerDevice.WEAR }
}

private val platformRepositoryModule = module {
    singleOf(::WearFileRepository)
    singleOf(::WearLinkRepository)
}

private val platformUseCaseModule = module {
    factoryOf(::FindModuleUpdateSourceUseCase)
    factoryOf(::OpenWearExternalLinkUseCase)
    factoryOf(::ResolveInstalledModuleIconUseCase)
}

private val platformViewModelModule = module {
    viewModelOf(::WearDownloadsViewModel)
    viewModelOf(::WearFileViewModel)
    viewModelOf(::WearLinkViewModel)
    viewModelOf(::WearPreferencesViewModel)
    viewModelOf(::ModuleDownloadViewModel)
    viewModel { parameters -> WearSearchViewModel(parameters[0], get(), get()) }
    viewModel { parameters -> ModuleChangelogViewModel(parameters.get(), get()) }
}

val platformModules = listOf(platformCoreModule, platformRepositoryModule, platformUseCaseModule, platformViewModelModule)
