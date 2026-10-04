package com.bakasu.bakasu.di

import coil.ImageLoader
import com.bakasu.bakasu.BuildConfig
import com.bakasu.bakasu.data.AppSettingsRepository
import com.bakasu.bakasu.data.application.ApplicationControlRepository
import com.bakasu.bakasu.data.application.DynamicManagerRepository
import com.bakasu.bakasu.data.count.CountRepository
import com.bakasu.bakasu.data.download.DownloadRepository
import com.bakasu.bakasu.data.file.ModuleFileRepository
import com.bakasu.bakasu.data.flash.FlashRepository
import com.bakasu.bakasu.data.kernel.KernelRepository
import com.bakasu.bakasu.data.kernel.UmountRepository
import com.bakasu.bakasu.data.logging.BugreportRepository
import com.bakasu.bakasu.data.logging.SulogRepository
import com.bakasu.bakasu.data.module.ModuleActionRepository
import com.bakasu.bakasu.data.module.ModuleCatalogRepository
import com.bakasu.bakasu.data.module.ModulePreferencesRepository
import com.bakasu.bakasu.data.module.ModuleRepository
import com.bakasu.bakasu.data.network.NetworkRequestRepository
import com.bakasu.bakasu.data.network.NetworkStatusRepository
import com.bakasu.bakasu.data.network.WebResourceRepository
import com.bakasu.bakasu.data.packageinfo.AppIconDataSource
import com.bakasu.bakasu.data.packageinfo.InstalledPackageCache
import com.bakasu.bakasu.data.packageinfo.InstalledPackageRepository
import com.bakasu.bakasu.data.packageinfo.RootServiceRepository
import com.bakasu.bakasu.data.packageinfo.SuperUserRepository
import com.bakasu.bakasu.data.profile.ProfileRepository
import com.bakasu.bakasu.data.profile.ProfileTemplateRepository
import com.bakasu.bakasu.data.settings.LocaleHelper
import com.bakasu.bakasu.data.settings.LocaleRepository
import com.bakasu.bakasu.data.settings.SettingsPlatformRepository
import com.bakasu.bakasu.data.shell.KsuCliRepository
import com.bakasu.bakasu.data.shell.ShortcutRepository
import com.bakasu.bakasu.data.startup.ApplicationInitializationRepository
import com.bakasu.bakasu.data.startup.StartupRepository
import com.bakasu.bakasu.data.susfs.SuSFSConfigHelper
import com.bakasu.bakasu.data.susfs.SuSFSRepository
import com.bakasu.bakasu.data.system.HomeRuntimeRepository
import com.bakasu.bakasu.data.system.HomeStateRepository
import com.bakasu.bakasu.data.text.HanziToPinyin
import com.bakasu.bakasu.data.theme.MonetCompatColorSource
import com.bakasu.bakasu.data.theme.ThemeRepository
import com.bakasu.bakasu.data.update.ManagerUpdateRepository
import com.bakasu.bakasu.data.webui.WebUiRepository
import com.bakasu.bakasu.domain.text.TextTransliterator
import com.bakasu.bakasu.domain.usecase.AddUmountPathUseCase
import com.bakasu.bakasu.domain.usecase.ApplyLanguageUseCase
import com.bakasu.bakasu.domain.usecase.BackupAllowlistUseCase
import com.bakasu.bakasu.domain.usecase.CalculateInstalledModuleSizeUseCase
import com.bakasu.bakasu.domain.usecase.CheckFlashModuleMountUseCase
import com.bakasu.bakasu.domain.usecase.CheckManagerUpdateUseCase
import com.bakasu.bakasu.domain.usecase.CleanSulogUseCase
import com.bakasu.bakasu.domain.usecase.ClearDynamicManagerUseCase
import com.bakasu.bakasu.domain.usecase.ConfigureSuLogUseCase
import com.bakasu.bakasu.domain.usecase.ControlAppUseCase
import com.bakasu.bakasu.domain.usecase.DeleteProfileTemplateUseCase
import com.bakasu.bakasu.domain.usecase.EnableSulogUseCase
import com.bakasu.bakasu.domain.usecase.EnqueueDownloadUseCase
import com.bakasu.bakasu.domain.usecase.EnqueueManagerUpdateUseCase
import com.bakasu.bakasu.domain.usecase.EnsureManagerInstalledUseCase
import com.bakasu.bakasu.domain.usecase.ExecuteFlashOperationUseCase
import com.bakasu.bakasu.domain.usecase.ExecuteModuleActionUseCase
import com.bakasu.bakasu.domain.usecase.ExportProfileTemplatesUseCase
import com.bakasu.bakasu.domain.usecase.ExtractModuleIdUseCase
import com.bakasu.bakasu.domain.usecase.ExtractModuleNameUseCase
import com.bakasu.bakasu.domain.usecase.FetchRemoteTextUseCase
import com.bakasu.bakasu.domain.usecase.GenerateBugreportUseCase
import com.bakasu.bakasu.domain.usecase.GetAppProfileUseCase
import com.bakasu.bakasu.domain.usecase.GetAppSepolicyUseCase
import com.bakasu.bakasu.domain.usecase.GetBooleanPreferenceUseCase
import com.bakasu.bakasu.domain.usecase.GetCatalogModuleUseCase
import com.bakasu.bakasu.domain.usecase.GetDefaultUmountModulesUseCase
import com.bakasu.bakasu.domain.usecase.GetHomeBasicInfoUseCase
import com.bakasu.bakasu.domain.usecase.GetInstallEnvironmentUseCase
import com.bakasu.bakasu.domain.usecase.GetKernelFeatureSettingsUseCase
import com.bakasu.bakasu.domain.usecase.GetKernelStatusUseCase
import com.bakasu.bakasu.domain.usecase.GetManagerRuntimeInfoUseCase
import com.bakasu.bakasu.domain.usecase.GetPlatformFeatureStatusUseCase
import com.bakasu.bakasu.domain.usecase.GetProfileTemplateUseCase
import com.bakasu.bakasu.domain.usecase.GetStringPreferenceUseCase
import com.bakasu.bakasu.domain.usecase.GetStringSetPreferenceUseCase
import com.bakasu.bakasu.domain.usecase.GetSuSFSStatusUseCase
import com.bakasu.bakasu.domain.usecase.GetSuperUserAppGroupUseCase
import com.bakasu.bakasu.domain.usecase.ImportAllowlistUseCase
import com.bakasu.bakasu.domain.usecase.ImportProfileTemplatesUseCase
import com.bakasu.bakasu.domain.usecase.InitializeApplicationUseCase
import com.bakasu.bakasu.domain.usecase.IsLateLoadModeUseCase
import com.bakasu.bakasu.domain.usecase.IsModuleUriAccessibleUseCase
import com.bakasu.bakasu.domain.usecase.IsNetworkAvailableUseCase
import com.bakasu.bakasu.domain.usecase.IsSoftRebootPreferredUseCase
import com.bakasu.bakasu.domain.usecase.IsSystemLanguageSettingsUseCase
import com.bakasu.bakasu.domain.usecase.LaunchSystemLanguageSettingsUseCase
import com.bakasu.bakasu.domain.usecase.LoadSettingsPlatformUseCase
import com.bakasu.bakasu.domain.usecase.ObserveCatalogModulesUseCase
import com.bakasu.bakasu.domain.usecase.ObserveDownloadUseCase
import com.bakasu.bakasu.domain.usecase.ObserveDynamicManagerStateUseCase
import com.bakasu.bakasu.domain.usecase.ObserveInstalledModulesUseCase
import com.bakasu.bakasu.domain.usecase.ObserveKernelFlashUseCase
import com.bakasu.bakasu.domain.usecase.ObserveModuleCatalogOfflineUseCase
import com.bakasu.bakasu.domain.usecase.ObserveModuleCatalogRefreshingUseCase
import com.bakasu.bakasu.domain.usecase.ObserveProfileTemplateOfflineUseCase
import com.bakasu.bakasu.domain.usecase.ObserveProfileTemplateRefreshingUseCase
import com.bakasu.bakasu.domain.usecase.ObserveProfileTemplatesUseCase
import com.bakasu.bakasu.domain.usecase.ObserveStartupStateUseCase
import com.bakasu.bakasu.domain.usecase.ObserveSulogStateUseCase
import com.bakasu.bakasu.domain.usecase.ObserveSuperUserStateUseCase
import com.bakasu.bakasu.domain.usecase.ObserveUmountStateUseCase
import com.bakasu.bakasu.domain.usecase.RebootUseCase
import com.bakasu.bakasu.domain.usecase.RefreshDynamicManagerUseCase
import com.bakasu.bakasu.domain.usecase.RefreshInstalledModulesUseCase
import com.bakasu.bakasu.domain.usecase.RefreshModuleCatalogUseCase
import com.bakasu.bakasu.domain.usecase.RefreshProfileTemplatesUseCase
import com.bakasu.bakasu.domain.usecase.RefreshSulogUseCase
import com.bakasu.bakasu.domain.usecase.RefreshSuperUsersUseCase
import com.bakasu.bakasu.domain.usecase.RefreshUmountPathsUseCase
import com.bakasu.bakasu.domain.usecase.RemovePreferenceUseCase
import com.bakasu.bakasu.domain.usecase.RemoveUmountPathUseCase
import com.bakasu.bakasu.domain.usecase.SaveModuleActionLogUseCase
import com.bakasu.bakasu.domain.usecase.SaveProfileTemplateUseCase
import com.bakasu.bakasu.domain.usecase.SelectDynamicManagerUseCase
import com.bakasu.bakasu.domain.usecase.SetAppProfileUseCase
import com.bakasu.bakasu.domain.usecase.SetAppSepolicyUseCase
import com.bakasu.bakasu.domain.usecase.SetBooleanPreferenceUseCase
import com.bakasu.bakasu.domain.usecase.SetDefaultUmountModulesUseCase
import com.bakasu.bakasu.domain.usecase.SetKernelUmountEnabledUseCase
import com.bakasu.bakasu.domain.usecase.SetManualDynamicManagerUseCase
import com.bakasu.bakasu.domain.usecase.SetModuleEnabledUseCase
import com.bakasu.bakasu.domain.usecase.SetModuleRemovedUseCase
import com.bakasu.bakasu.domain.usecase.SetSelinuxHideEnabledUseCase
import com.bakasu.bakasu.domain.usecase.SetStringPreferenceUseCase
import com.bakasu.bakasu.domain.usecase.SetStringSetPreferenceUseCase
import com.bakasu.bakasu.domain.usecase.SetSuEnabledUseCase
import com.bakasu.bakasu.domain.usecase.StartKernelFlashUseCase
import com.bakasu.bakasu.domain.usecase.SuSFSConfigUseCase
import com.bakasu.bakasu.domain.usecase.TakeModuleUriPermissionUseCase
import com.bakasu.bakasu.domain.usecase.TransliterateTextUseCase
import com.bakasu.bakasu.domain.usecase.UpdateAppearanceUseCase
import com.bakasu.bakasu.domain.usecase.UpdateCachedModuleEnabledUseCase
import com.bakasu.bakasu.domain.usecase.UpdatePlatformSettingUseCase
import com.bakasu.bakasu.domain.usecase.ValidateSepolicyUseCase
import com.bakasu.bakasu.ui.activity.util.ThemeUtils
import com.bakasu.bakasu.ui.component.ZipFileDetector
import com.bakasu.bakasu.ui.theme.BackgroundManager
import com.bakasu.bakasu.ui.theme.CardConfig
import com.bakasu.bakasu.ui.theme.ThemeConfig
import com.bakasu.bakasu.ui.util.module.Shortcut
import com.bakasu.bakasu.ui.viewmodel.AppProfileViewModel
import com.bakasu.bakasu.ui.viewmodel.DynamicManagerViewModel
import com.bakasu.bakasu.ui.viewmodel.ExecuteModuleActionViewModel
import com.bakasu.bakasu.ui.viewmodel.FlashViewModel
import com.bakasu.bakasu.ui.viewmodel.HomeViewModel
import com.bakasu.bakasu.ui.viewmodel.InstallViewModel
import com.bakasu.bakasu.ui.viewmodel.KernelFlashViewModel
import com.bakasu.bakasu.ui.viewmodel.MainIntentViewModel
import com.bakasu.bakasu.ui.viewmodel.ModuleDetailViewModel
import com.bakasu.bakasu.ui.viewmodel.ModuleRepoViewModel
import com.bakasu.bakasu.ui.viewmodel.ModuleViewModel
import com.bakasu.bakasu.ui.viewmodel.SettingsViewModel
import com.bakasu.bakasu.ui.viewmodel.SuSFSViewModel
import com.bakasu.bakasu.ui.viewmodel.SulogViewModel
import com.bakasu.bakasu.ui.viewmodel.SuperUserViewModel
import com.bakasu.bakasu.ui.viewmodel.TemplateEditorViewModel
import com.bakasu.bakasu.ui.viewmodel.TemplateViewModel
import com.bakasu.bakasu.ui.viewmodel.UmountManagerScreenViewModel
import com.bakasu.bakasu.ui.webui.MonetColorsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

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
                        .build()
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
