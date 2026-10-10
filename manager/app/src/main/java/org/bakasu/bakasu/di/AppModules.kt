package org.bakasu.bakasu.di

import coil.ImageLoader
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.bakasu.bakasu.BuildConfig
import org.bakasu.bakasu.data.AppSettingsRepository
import org.bakasu.bakasu.data.application.ApplicationControlRepository
import org.bakasu.bakasu.data.application.DynamicManagerRepository
import org.bakasu.bakasu.data.count.CountRepository
import org.bakasu.bakasu.data.download.DownloadRepository
import org.bakasu.bakasu.data.file.ModuleFileRepository
import org.bakasu.bakasu.data.flash.FlashRepository
import org.bakasu.bakasu.data.kernel.KernelRepository
import org.bakasu.bakasu.data.kernel.UmountRepository
import org.bakasu.bakasu.data.logging.BugreportRepository
import org.bakasu.bakasu.data.logging.SulogRepository
import org.bakasu.bakasu.data.module.ModuleActionRepository
import org.bakasu.bakasu.data.module.ModuleCatalogRepository
import org.bakasu.bakasu.data.module.ModulePreferencesRepository
import org.bakasu.bakasu.data.module.ModuleRepository
import org.bakasu.bakasu.data.network.NetworkRequestRepository
import org.bakasu.bakasu.data.network.NetworkStatusRepository
import org.bakasu.bakasu.data.network.WebResourceRepository
import org.bakasu.bakasu.data.packageinfo.AppIconDataSource
import org.bakasu.bakasu.data.packageinfo.InstalledPackageCache
import org.bakasu.bakasu.data.packageinfo.InstalledPackageRepository
import org.bakasu.bakasu.data.packageinfo.RootServiceRepository
import org.bakasu.bakasu.data.packageinfo.SuperUserRepository
import org.bakasu.bakasu.data.profile.ProfileRepository
import org.bakasu.bakasu.data.profile.ProfileTemplateRepository
import org.bakasu.bakasu.data.settings.LocaleHelper
import org.bakasu.bakasu.data.settings.LocaleRepository
import org.bakasu.bakasu.data.settings.SettingsPlatformRepository
import org.bakasu.bakasu.data.shell.KsuCliRepository
import org.bakasu.bakasu.data.shell.ShortcutRepository
import org.bakasu.bakasu.data.startup.ApplicationInitializationRepository
import org.bakasu.bakasu.data.startup.StartupRepository
import org.bakasu.bakasu.data.susfs.SuSFSConfigHelper
import org.bakasu.bakasu.data.susfs.SuSFSRepository
import org.bakasu.bakasu.data.system.HomeRuntimeRepository
import org.bakasu.bakasu.data.system.HomeStateRepository
import org.bakasu.bakasu.data.text.HanziToPinyin
import org.bakasu.bakasu.data.theme.MonetCompatColorSource
import org.bakasu.bakasu.data.theme.ThemeRepository
import org.bakasu.bakasu.data.update.ManagerUpdateRepository
import org.bakasu.bakasu.data.webui.WebUiRepository
import org.bakasu.bakasu.domain.text.TextTransliterator
import org.bakasu.bakasu.domain.usecase.AddUmountPathUseCase
import org.bakasu.bakasu.domain.usecase.ApplyLanguageUseCase
import org.bakasu.bakasu.domain.usecase.BackupAllowlistUseCase
import org.bakasu.bakasu.domain.usecase.CalculateInstalledModuleSizeUseCase
import org.bakasu.bakasu.domain.usecase.CheckFlashModuleMountUseCase
import org.bakasu.bakasu.domain.usecase.CheckManagerUpdateUseCase
import org.bakasu.bakasu.domain.usecase.CleanSulogUseCase
import org.bakasu.bakasu.domain.usecase.ClearDynamicManagerUseCase
import org.bakasu.bakasu.domain.usecase.ConfigureSuLogUseCase
import org.bakasu.bakasu.domain.usecase.ControlAppUseCase
import org.bakasu.bakasu.domain.usecase.DeleteProfileTemplateUseCase
import org.bakasu.bakasu.domain.usecase.EnableSulogUseCase
import org.bakasu.bakasu.domain.usecase.EnqueueDownloadUseCase
import org.bakasu.bakasu.domain.usecase.EnqueueManagerUpdateUseCase
import org.bakasu.bakasu.domain.usecase.EnsureManagerInstalledUseCase
import org.bakasu.bakasu.domain.usecase.ExecuteFlashOperationUseCase
import org.bakasu.bakasu.domain.usecase.ExecuteModuleActionUseCase
import org.bakasu.bakasu.domain.usecase.ExportProfileTemplatesUseCase
import org.bakasu.bakasu.domain.usecase.ExtractModuleIdUseCase
import org.bakasu.bakasu.domain.usecase.ExtractModuleNameUseCase
import org.bakasu.bakasu.domain.usecase.FetchRemoteTextUseCase
import org.bakasu.bakasu.domain.usecase.GenerateBugreportUseCase
import org.bakasu.bakasu.domain.usecase.GetAppProfileUseCase
import org.bakasu.bakasu.domain.usecase.GetAppSepolicyUseCase
import org.bakasu.bakasu.domain.usecase.GetBooleanPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.GetCatalogModuleUseCase
import org.bakasu.bakasu.domain.usecase.GetDefaultUmountModulesUseCase
import org.bakasu.bakasu.domain.usecase.GetHomeBasicInfoUseCase
import org.bakasu.bakasu.domain.usecase.GetInstallEnvironmentUseCase
import org.bakasu.bakasu.domain.usecase.GetKernelFeatureSettingsUseCase
import org.bakasu.bakasu.domain.usecase.GetKernelStatusUseCase
import org.bakasu.bakasu.domain.usecase.GetManagerRuntimeInfoUseCase
import org.bakasu.bakasu.domain.usecase.GetPlatformFeatureStatusUseCase
import org.bakasu.bakasu.domain.usecase.GetProfileTemplateUseCase
import org.bakasu.bakasu.domain.usecase.GetStringPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.GetStringSetPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.GetSuSFSStatusUseCase
import org.bakasu.bakasu.domain.usecase.GetSuperUserAppGroupUseCase
import org.bakasu.bakasu.domain.usecase.ImportAllowlistUseCase
import org.bakasu.bakasu.domain.usecase.ImportProfileTemplatesUseCase
import org.bakasu.bakasu.domain.usecase.InitializeApplicationUseCase
import org.bakasu.bakasu.domain.usecase.IsLateLoadModeUseCase
import org.bakasu.bakasu.domain.usecase.IsModuleUriAccessibleUseCase
import org.bakasu.bakasu.domain.usecase.IsNetworkAvailableUseCase
import org.bakasu.bakasu.domain.usecase.IsSoftRebootPreferredUseCase
import org.bakasu.bakasu.domain.usecase.IsSystemLanguageSettingsUseCase
import org.bakasu.bakasu.domain.usecase.LaunchSystemLanguageSettingsUseCase
import org.bakasu.bakasu.domain.usecase.LoadSettingsPlatformUseCase
import org.bakasu.bakasu.domain.usecase.ObserveCatalogModulesUseCase
import org.bakasu.bakasu.domain.usecase.ObserveDownloadUseCase
import org.bakasu.bakasu.domain.usecase.ObserveDynamicManagerStateUseCase
import org.bakasu.bakasu.domain.usecase.ObserveInstalledModulesUseCase
import org.bakasu.bakasu.domain.usecase.ObserveKernelFlashUseCase
import org.bakasu.bakasu.domain.usecase.ObserveModuleCatalogOfflineUseCase
import org.bakasu.bakasu.domain.usecase.ObserveModuleCatalogRefreshingUseCase
import org.bakasu.bakasu.domain.usecase.ObserveProfileTemplateOfflineUseCase
import org.bakasu.bakasu.domain.usecase.ObserveProfileTemplateRefreshingUseCase
import org.bakasu.bakasu.domain.usecase.ObserveProfileTemplatesUseCase
import org.bakasu.bakasu.domain.usecase.ObserveStartupStateUseCase
import org.bakasu.bakasu.domain.usecase.ObserveSulogStateUseCase
import org.bakasu.bakasu.domain.usecase.ObserveSuperUserStateUseCase
import org.bakasu.bakasu.domain.usecase.ObserveUmountStateUseCase
import org.bakasu.bakasu.domain.usecase.RebootUseCase
import org.bakasu.bakasu.domain.usecase.RefreshDynamicManagerUseCase
import org.bakasu.bakasu.domain.usecase.RefreshInstalledModulesUseCase
import org.bakasu.bakasu.domain.usecase.RefreshModuleCatalogUseCase
import org.bakasu.bakasu.domain.usecase.RefreshProfileTemplatesUseCase
import org.bakasu.bakasu.domain.usecase.RefreshSulogUseCase
import org.bakasu.bakasu.domain.usecase.RefreshSuperUsersUseCase
import org.bakasu.bakasu.domain.usecase.RefreshUmountPathsUseCase
import org.bakasu.bakasu.domain.usecase.RemovePreferenceUseCase
import org.bakasu.bakasu.domain.usecase.RemoveUmountPathUseCase
import org.bakasu.bakasu.domain.usecase.SaveModuleActionLogUseCase
import org.bakasu.bakasu.domain.usecase.SaveProfileTemplateUseCase
import org.bakasu.bakasu.domain.usecase.SelectDynamicManagerUseCase
import org.bakasu.bakasu.domain.usecase.SetAppProfileUseCase
import org.bakasu.bakasu.domain.usecase.SetAppSepolicyUseCase
import org.bakasu.bakasu.domain.usecase.SetBooleanPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.SetDefaultUmountModulesUseCase
import org.bakasu.bakasu.domain.usecase.SetKernelUmountEnabledUseCase
import org.bakasu.bakasu.domain.usecase.SetManualDynamicManagerUseCase
import org.bakasu.bakasu.domain.usecase.SetModuleEnabledUseCase
import org.bakasu.bakasu.domain.usecase.SetModuleRemovedUseCase
import org.bakasu.bakasu.domain.usecase.SetSelinuxHideEnabledUseCase
import org.bakasu.bakasu.domain.usecase.SetStringPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.SetStringSetPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.SetSuEnabledUseCase
import org.bakasu.bakasu.domain.usecase.StartKernelFlashUseCase
import org.bakasu.bakasu.domain.usecase.SuSFSConfigUseCase
import org.bakasu.bakasu.domain.usecase.TakeModuleUriPermissionUseCase
import org.bakasu.bakasu.domain.usecase.TransliterateTextUseCase
import org.bakasu.bakasu.domain.usecase.UpdateAppearanceUseCase
import org.bakasu.bakasu.domain.usecase.UpdateCachedModuleEnabledUseCase
import org.bakasu.bakasu.domain.usecase.UpdatePlatformSettingUseCase
import org.bakasu.bakasu.domain.usecase.ValidateSepolicyUseCase
import org.bakasu.bakasu.ui.activity.util.ThemeUtils
import org.bakasu.bakasu.ui.component.ZipFileDetector
import org.bakasu.bakasu.ui.theme.BackgroundManager
import org.bakasu.bakasu.ui.theme.CardConfig
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.util.Shortcut
import org.bakasu.bakasu.ui.viewmodel.AppProfileViewModel
import org.bakasu.bakasu.ui.viewmodel.DynamicManagerViewModel
import org.bakasu.bakasu.ui.viewmodel.ExecuteModuleActionViewModel
import org.bakasu.bakasu.ui.viewmodel.FlashViewModel
import org.bakasu.bakasu.ui.viewmodel.HomeViewModel
import org.bakasu.bakasu.ui.viewmodel.InstallViewModel
import org.bakasu.bakasu.ui.viewmodel.KernelFlashViewModel
import org.bakasu.bakasu.ui.viewmodel.MainIntentViewModel
import org.bakasu.bakasu.ui.viewmodel.ModuleDetailViewModel
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoViewModel
import org.bakasu.bakasu.ui.viewmodel.ModuleViewModel
import org.bakasu.bakasu.ui.viewmodel.SettingsViewModel
import org.bakasu.bakasu.ui.viewmodel.SuSFSViewModel
import org.bakasu.bakasu.ui.viewmodel.SulogViewModel
import org.bakasu.bakasu.ui.viewmodel.SuperUserViewModel
import org.bakasu.bakasu.ui.viewmodel.TemplateEditorViewModel
import org.bakasu.bakasu.ui.viewmodel.TemplateViewModel
import org.bakasu.bakasu.ui.viewmodel.UmountManagerScreenViewModel
import org.bakasu.bakasu.ui.webui.MonetColorsProvider
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val applicationScopeQualifier = named("applicationScope")

val coreModule = module {
    single<CoroutineScope>(applicationScopeQualifier) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    single {
        OkHttpClient.Builder()
            .cache(Cache(File(androidApplication().cacheDir, "okhttp"), 10L * 1024L * 1024L))
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "BakaSU/${BuildConfig.VERSION_CODE}")
                        .header("Accept-Language", Locale.getDefault().toLanguageTag())
                        .build(),
                )
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    single {
        val application = androidApplication()
        val iconSize = application.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        ImageLoader.Builder(application)
            .components {
                add(AppIconKeyer())
                add(AppIconFetcher.Factory(iconSize, false, application))
            }
            .build()
    }
}

val repositoryModule = module {
    single { KsuCliRepository(androidApplication()) }
    singleOf(::CountRepository)
    singleOf(::InstalledPackageCache)
    singleOf(::AppIconDataSource)
    singleOf(::RootServiceRepository)
    singleOf(::InstalledPackageRepository)
    single {
        SuperUserRepository(
            application = get(),
            cache = get(),
            installedPackageRepository = get(),
            profileRepository = get(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    single {
        AppSettingsRepository(
            context = androidApplication(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    singleOf(::StartupRepository)
    single {
        ApplicationInitializationRepository(
            application = get(),
            imageLoader = get(),
            applicationScope = get(applicationScopeQualifier),
            flashRepository = get(),
            ksuCliRepository = get(),
            monetCompatColorSource = get(),
        )
    }
    singleOf(::ManagerUpdateRepository)
    singleOf(::ApplicationControlRepository)
    singleOf(::DownloadRepository)
    single { FlashRepository(get(), get(applicationScopeQualifier), get(), get()) }
    singleOf(::KernelRepository)
    singleOf(::HomeRuntimeRepository)
    singleOf(::HomeStateRepository)
    singleOf(::NetworkStatusRepository)
    singleOf(::NetworkRequestRepository)
    singleOf(::DynamicManagerRepository)
    singleOf(::SulogRepository)
    singleOf(::BugreportRepository)
    singleOf(::UmountRepository)
    singleOf(::ModuleCatalogRepository)
    singleOf(::ModuleRepository)
    singleOf(::ModulePreferencesRepository)
    singleOf(::ModuleActionRepository)
    singleOf(::WebResourceRepository)
    singleOf(::WebUiRepository)
    singleOf(::ModuleFileRepository)
    singleOf(::ProfileRepository)
    singleOf(::ProfileTemplateRepository)
    singleOf(::SuSFSConfigHelper)
    singleOf(::SuSFSRepository)
    singleOf(::MonetCompatColorSource)
    singleOf(::ThemeRepository)
    single {
        val themeRepository = get<ThemeRepository>()
        ThemeConfig(themeRepository::defaultSeedColor)
    }
    singleOf(::CardConfig)
    singleOf(::BackgroundManager)
    singleOf(::ThemeUtils)
    singleOf(::LocaleHelper)
    singleOf(::LocaleRepository)
    singleOf(::SettingsPlatformRepository)
    singleOf(::ShortcutRepository)
    singleOf(::Shortcut)
    singleOf(::MonetColorsProvider)
    singleOf(::ZipFileDetector)
    single { HanziToPinyin.create() } bind TextTransliterator::class
}

val useCaseModule = module {
    factoryOf(::InitializeApplicationUseCase)
    factoryOf(::GetHomeBasicInfoUseCase)
    factoryOf(::IsNetworkAvailableUseCase)
    factoryOf(::LoadSettingsPlatformUseCase)
    factoryOf(::UpdateAppearanceUseCase)
    factoryOf(::UpdatePlatformSettingUseCase)
    factoryOf(::GetPlatformFeatureStatusUseCase)
    factoryOf(::IsSoftRebootPreferredUseCase)
    factoryOf(::CheckManagerUpdateUseCase)
    factoryOf(::EnsureManagerInstalledUseCase)
    factoryOf(::RebootUseCase)
    factoryOf(::EnqueueDownloadUseCase)
    factoryOf(::EnqueueManagerUpdateUseCase)
    factoryOf(::ObserveDownloadUseCase)
    factoryOf(::GetKernelStatusUseCase)
    factoryOf(::GetInstallEnvironmentUseCase)
    factoryOf(::ExecuteFlashOperationUseCase)
    factoryOf(::CheckFlashModuleMountUseCase)
    factoryOf(::GetManagerRuntimeInfoUseCase)
    factoryOf(::GetKernelFeatureSettingsUseCase)
    factoryOf(::SetSuEnabledUseCase)
    factoryOf(::SetKernelUmountEnabledUseCase)
    factoryOf(::ConfigureSuLogUseCase)
    factoryOf(::SetSelinuxHideEnabledUseCase)
    factoryOf(::SetDefaultUmountModulesUseCase)
    factoryOf(::IsLateLoadModeUseCase)
    factoryOf(::GetAppProfileUseCase)
    factoryOf(::SetAppProfileUseCase)
    factoryOf(::GetAppSepolicyUseCase)
    factoryOf(::SetAppSepolicyUseCase)
    factoryOf(::ControlAppUseCase)
    factoryOf(::ValidateSepolicyUseCase)
    factoryOf(::GetDefaultUmountModulesUseCase)
    factoryOf(::GetSuSFSStatusUseCase)
    factoryOf(::SuSFSConfigUseCase)
    factoryOf(::ApplyLanguageUseCase)
    factoryOf(::IsSystemLanguageSettingsUseCase)
    factoryOf(::LaunchSystemLanguageSettingsUseCase)
    factoryOf(::GenerateBugreportUseCase)
    factoryOf(::ObserveStartupStateUseCase)
    factoryOf(::GetSuperUserAppGroupUseCase)
    factoryOf(::ObserveCatalogModulesUseCase)
    factoryOf(::ObserveModuleCatalogRefreshingUseCase)
    factoryOf(::ObserveModuleCatalogOfflineUseCase)
    factoryOf(::RefreshModuleCatalogUseCase)
    factoryOf(::GetCatalogModuleUseCase)
    factoryOf(::ObserveProfileTemplatesUseCase)
    factoryOf(::ObserveProfileTemplateRefreshingUseCase)
    factoryOf(::ObserveProfileTemplateOfflineUseCase)
    factoryOf(::RefreshProfileTemplatesUseCase)
    factoryOf(::GetProfileTemplateUseCase)
    factoryOf(::SaveProfileTemplateUseCase)
    factoryOf(::DeleteProfileTemplateUseCase)
    factoryOf(::ImportProfileTemplatesUseCase)
    factoryOf(::ExportProfileTemplatesUseCase)
    factoryOf(::GetBooleanPreferenceUseCase)
    factoryOf(::SetBooleanPreferenceUseCase)
    factoryOf(::GetStringPreferenceUseCase)
    factoryOf(::SetStringPreferenceUseCase)
    factoryOf(::GetStringSetPreferenceUseCase)
    factoryOf(::SetStringSetPreferenceUseCase)
    factoryOf(::ObserveDynamicManagerStateUseCase)
    factoryOf(::RefreshDynamicManagerUseCase)
    factoryOf(::SelectDynamicManagerUseCase)
    factoryOf(::SetManualDynamicManagerUseCase)
    factoryOf(::ClearDynamicManagerUseCase)
    factoryOf(::ObserveSulogStateUseCase)
    factoryOf(::RefreshSulogUseCase)
    factoryOf(::EnableSulogUseCase)
    factoryOf(::CleanSulogUseCase)
    factoryOf(::ObserveUmountStateUseCase)
    factoryOf(::RefreshUmountPathsUseCase)
    factoryOf(::AddUmountPathUseCase)
    factoryOf(::RemoveUmountPathUseCase)
    factoryOf(::ObserveKernelFlashUseCase)
    factoryOf(::StartKernelFlashUseCase)
    factoryOf(::RemovePreferenceUseCase)
    factoryOf(::ObserveSuperUserStateUseCase)
    factoryOf(::RefreshSuperUsersUseCase)
    factoryOf(::BackupAllowlistUseCase)
    factoryOf(::ImportAllowlistUseCase)
    factoryOf(::FetchRemoteTextUseCase)
    factoryOf(::IsModuleUriAccessibleUseCase)
    factoryOf(::TakeModuleUriPermissionUseCase)
    factoryOf(::ExtractModuleNameUseCase)
    factoryOf(::ExtractModuleIdUseCase)
    factoryOf(::ObserveInstalledModulesUseCase)
    factoryOf(::RefreshInstalledModulesUseCase)
    factoryOf(::CalculateInstalledModuleSizeUseCase)
    factoryOf(::UpdateCachedModuleEnabledUseCase)
    factoryOf(::ExecuteModuleActionUseCase)
    factoryOf(::SaveModuleActionLogUseCase)
    factoryOf(::SetModuleEnabledUseCase)
    factoryOf(::SetModuleRemovedUseCase)
    factoryOf(::TransliterateTextUseCase)
}

val viewModelModule = module {
    viewModel { parameters ->
        AppProfileViewModel(
            uid = parameters[0],
            packageName = parameters[1],
            getAppGroup = get(),
            getProfile = get(),
            getDefaultUmountModules = get(),
            setProfile = get(),
            getSepolicy = get(),
            setSepolicy = get(),
            controlApp = get(),
            validateSepolicy = get(),
        )
    }
    viewModelOf(::HomeViewModel)
    viewModelOf(::InstallViewModel)
    viewModelOf(::MainIntentViewModel)
    viewModelOf(::KernelFlashViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ModuleViewModel)
    viewModelOf(::SuperUserViewModel)
    viewModelOf(::SuSFSViewModel)
    viewModelOf(::ModuleRepoViewModel)
    viewModel { parameters -> ModuleDetailViewModel(parameters[0], get()) }
    viewModelOf(::TemplateViewModel)
    viewModel { parameters ->
        TemplateEditorViewModel(
            templateId = parameters[0],
            readOnly = parameters[1],
            isCreation = parameters[2],
            getTemplate = get(),
            saveTemplate = get(),
            deleteTemplate = get(),
        )
    }
    viewModelOf(::SulogViewModel)
    viewModelOf(::DynamicManagerViewModel)
    viewModelOf(::FlashViewModel)
    viewModelOf(::UmountManagerScreenViewModel)
    viewModel { parameters ->
        ExecuteModuleActionViewModel(
            moduleId = parameters[0],
            executeModuleAction = get(),
            saveModuleActionLog = get(),
        )
    }
}

val appModules = listOf(coreModule, repositoryModule, useCaseModule, viewModelModule)
