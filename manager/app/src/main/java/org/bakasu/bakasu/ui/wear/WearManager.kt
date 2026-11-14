package org.bakasu.bakasu.ui.wear

import android.content.Intent
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.PagerScaffoldDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import kotlinx.coroutines.channels.ReceiveChannel
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.FlashOperation
import org.bakasu.bakasu.domain.model.HomeDashboardState
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.domain.model.LkmSelection
import org.bakasu.bakasu.domain.model.SuperUserLoadFailure
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.util.ActivityResumeEffect
import org.bakasu.bakasu.ui.viewmodel.DynamicManagerOperation
import org.bakasu.bakasu.ui.viewmodel.DynamicManagerUiAction
import org.bakasu.bakasu.ui.viewmodel.DynamicManagerUiEvent
import org.bakasu.bakasu.ui.viewmodel.DynamicManagerViewModel
import org.bakasu.bakasu.ui.viewmodel.HomeUiAction
import org.bakasu.bakasu.ui.viewmodel.HomeUiEvent
import org.bakasu.bakasu.ui.viewmodel.HomeViewModel
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoUiAction
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoViewModel
import org.bakasu.bakasu.ui.viewmodel.ModuleUiAction
import org.bakasu.bakasu.ui.viewmodel.ModuleUiEvent
import org.bakasu.bakasu.ui.viewmodel.ModuleUiState
import org.bakasu.bakasu.ui.viewmodel.ModuleViewModel
import org.bakasu.bakasu.ui.viewmodel.OnlineTemplateUiAction
import org.bakasu.bakasu.ui.viewmodel.OnlineTemplateViewModel
import org.bakasu.bakasu.ui.viewmodel.SettingsUiAction
import org.bakasu.bakasu.ui.viewmodel.SettingsUiEvent
import org.bakasu.bakasu.ui.viewmodel.SettingsUiState
import org.bakasu.bakasu.ui.viewmodel.SettingsViewModel
import org.bakasu.bakasu.ui.viewmodel.SulogLoading
import org.bakasu.bakasu.ui.viewmodel.SulogUiAction
import org.bakasu.bakasu.ui.viewmodel.SulogUiEvent
import org.bakasu.bakasu.ui.viewmodel.SulogViewModel
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiAction
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiState
import org.bakasu.bakasu.ui.viewmodel.SuperUserViewModel
import org.bakasu.bakasu.ui.viewmodel.TemplateUiAction
import org.bakasu.bakasu.ui.viewmodel.TemplateViewModel
import org.bakasu.bakasu.ui.viewmodel.UmountManagerScreenViewModel
import org.bakasu.bakasu.ui.viewmodel.UmountManagerUiAction
import org.bakasu.bakasu.ui.viewmodel.WearLinkViewModel
import org.bakasu.bakasu.ui.viewmodel.WearPreferences
import org.bakasu.bakasu.ui.viewmodel.WearPreferencesViewModel
import org.bakasu.bakasu.ui.wear.component.LocalWearPanelPull
import org.bakasu.bakasu.ui.wear.component.LocalWearToast
import org.bakasu.bakasu.ui.wear.component.WearBackground
import org.bakasu.bakasu.ui.wear.component.WearDialogIcon
import org.bakasu.bakasu.ui.wear.component.WearErrorDialog
import org.bakasu.bakasu.ui.wear.component.WearHorizontalPageIndicator
import org.bakasu.bakasu.ui.wear.component.WearHorizontalPager
import org.bakasu.bakasu.ui.wear.component.WearLoadingScreen
import org.bakasu.bakasu.ui.wear.component.WearManagerUpdateDialog
import org.bakasu.bakasu.ui.wear.component.WearOperationsBackHandler
import org.bakasu.bakasu.ui.wear.component.WearOperationsHost
import org.bakasu.bakasu.ui.wear.component.WearResult
import org.bakasu.bakasu.ui.wear.component.WearResultDialog
import org.bakasu.bakasu.ui.wear.component.WearSearchPage
import org.bakasu.bakasu.ui.wear.component.WearSwipeToDismissBox
import org.bakasu.bakasu.ui.wear.component.WearTextInputPage
import org.bakasu.bakasu.ui.wear.component.WearTimeText
import org.bakasu.bakasu.ui.wear.component.WearTimeTitleLayout
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val HOME = 0
private const val SUPERUSER = 1
private const val MODULES = 2
private const val SETTINGS = 3

@Composable
fun WearManagerScreen(intentChannel: ReceiveChannel<Intent>) {
    val activity = LocalActivity.current
    val resources = LocalResources.current
    val toast = LocalWearToast.current
    // The shared models belong to the activity, outside the back stack, as on the phone.
    val homeViewModel = koinViewModel<HomeViewModel>()
    val superUserViewModel = koinViewModel<SuperUserViewModel>()
    val moduleViewModel = koinViewModel<ModuleViewModel>()
    // Shared by the repository list and its search page, which are separate routes.
    val moduleRepoViewModel = koinViewModel<ModuleRepoViewModel>()
    val umountViewModel = koinViewModel<UmountManagerScreenViewModel>()
    val umount by umountViewModel.state.collectAsStateWithLifecycle()
    val templateViewModel = koinViewModel<TemplateViewModel>()
    val templates by templateViewModel.uiState.collectAsStateWithLifecycle()
    val importTemplates = rememberWearTemplateImport(templateViewModel)
    val sulogViewModel = koinViewModel<SulogViewModel>()
    val dynamicManagerViewModel = koinViewModel<DynamicManagerViewModel>()
    val settingsViewModel = koinViewModel<SettingsViewModel>()
    val preferenceViewModel = koinViewModel<WearPreferencesViewModel>()
    val linkViewModel = koinViewModel<WearLinkViewModel>()
    val linkLoading by linkViewModel.loading.collectAsStateWithLifecycle()
    val linkTransfer by linkViewModel.transfer.collectAsStateWithLifecycle()
    val linkPermissionRequest by linkViewModel.permissionRequest.collectAsStateWithLifecycle()
    val showLinkLoading = linkLoading && linkTransfer == null && linkPermissionRequest == null
    val themeConfig = koinInject<ThemeConfig>()
    val preferences by preferenceViewModel.state.collectAsStateWithLifecycle()
    var mainTitle by rememberSaveable { mutableIntStateOf(R.string.home) }
    val home by homeViewModel.uiState.collectAsStateWithLifecycle()
    val superuser by superUserViewModel.uiState.collectAsStateWithLifecycle()
    val modules by moduleViewModel.uiState.collectAsStateWithLifecycle()
    val logs by sulogViewModel.uiState.collectAsStateWithLifecycle()
    val catalog by moduleRepoViewModel.state.collectAsStateWithLifecycle()
    val dynamicManager by dynamicManagerViewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()

    var settingsError by remember { mutableStateOf<String?>(null) }
    var settingsMessageResource by remember { mutableStateOf<Pair<Int, Int?>?>(null) }
    var moduleError by remember { mutableStateOf<String?>(null) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var logError by remember { mutableStateOf<String?>(null) }
    var dynamicManagerResult by remember { mutableStateOf<WearResult?>(null) }
    var signatureSize by rememberSaveable { mutableStateOf("") }
    var signatureHash by rememberSaveable { mutableStateOf("") }
    val operationFailedText = stringResource(R.string.operation_failed)
    // Missing root is explained by the home warning; other load failures show on the list.
    val superuserError = superuser.loadFailure
        ?.takeIf { it != SuperUserLoadFailure.ROOT_ACCESS_REQUIRED }
        ?.let { stringResource(R.string.wear_load_failed) }

    // Settings feedback belongs to the page that caused it; navigating clears it.
    fun clearSettingsFeedback() {
        settingsError = null
        settingsMessageResource = null
    }
    val navigator = rememberWearNavigator(
        onChange = {
            clearSettingsFeedback()
            linkViewModel.onNavigation()
        },
        // Search results live on their own page; leaving it clears the query of its list.
        onLeave = { route ->
            when (route) {
                WearRoute.AppSearchResults -> superUserViewModel.dispatch(SuperUserUiAction.Search(""))
                WearRoute.ModuleSearchResults -> moduleViewModel.dispatch(ModuleUiAction.Search(""))
                WearRoute.ModuleRepo, WearRoute.ModuleRepoSearchResults -> moduleRepoViewModel.dispatch(ModuleRepoUiAction.Search(""))
                WearRoute.LogSearchResults -> sulogViewModel.dispatch(SulogUiAction.Search(""))
                WearRoute.TemplateSearchResults -> templateViewModel.dispatch(TemplateUiAction.Search(""))
                else -> Unit
            }
        },
        onExit = { activity?.finish() },
    )
    fun open(route: WearRoute) {
        if (navigator.top != route) {
            when (route) {
                WearRoute.Logs -> {
                    logError = null
                    sulogViewModel.dispatch(SulogUiAction.LoadPage)
                }

                WearRoute.DynamicManagerApps -> dynamicManagerViewModel.dispatch(DynamicManagerUiAction.Search(""))

                else -> Unit
            }
        }
        navigator.open(route)
    }
    fun back() = navigator.back()
    val repositoryPageLoading = rememberWearRepositoryLoading(WearRoute.ModuleRepo in navigator.backStack, catalog.repositoryUrl) { moduleRepoViewModel.refresh() }
    val templateManagementLoading = rememberWearTemplateLoading(WearRoute.Templates.takeIf { it in navigator.backStack }) { templateViewModel.fetchTemplates() }
    val templateChoice = navigator.backStack.filterIsInstance<WearRoute.AppProfileTemplateChoice>().lastOrNull()
    val templateChoiceLoading = rememberWearTemplateLoading(templateChoice) { templateViewModel.fetchTemplates() }
    val umountOpen = WearRoute.Umount in navigator.backStack
    LaunchedEffect(umountOpen) {
        if (umountOpen) umountViewModel.dispatch(UmountManagerUiAction.Refresh())
    }
    val operationsOnTop = navigator.top is WearRoute.Operations
    val appSearchInput = { open(WearRoute.AppSearch) }
    val moduleSearchInput = { open(WearRoute.ModuleSearch) }
    val logSearchInput = { open(WearRoute.LogSearch) }

    val installer = rememberWearModuleInstaller(navigator, onPickFailed = { moduleError = operationFailedText }) {
        moduleViewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
        moduleViewModel.dispatch(ModuleUiAction.Refresh())
        homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false))
    }
    val selectImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) settingsViewModel.dispatch(SettingsUiAction.SetCustomBackground(uri.toString()))
    }
    val dispatchSettings: (SettingsUiAction) -> Unit = {
        clearSettingsFeedback()
        settingsViewModel.dispatch(it)
    }
    // Ordinary switches speak through their state; selected settings results use a toast.
    // Blocking failures and follow-up instructions stay visible until the user dismisses them.
    val settingsNotice = settingsMessageResource?.let { (id, arg) ->
        if (arg == null) stringResource(id) else stringResource(id, arg)
    }
    val settingsNoticeDialog = rememberCustomDialog { dismiss ->
        val close = {
            dismiss()
            clearSettingsFeedback()
        }
        AlertDialog(
            visible = true,
            onDismissRequest = close,
            icon = { WearDialogIcon(Icons.Rounded.Info) },
            title = { Text(stringResource(R.string.settings)) },
            text = { Text(settingsNotice.orEmpty()) },
            edgeButton = { AlertDialogDefaults.EdgeButton(onClick = close) },
        )
    }
    LaunchedEffect(settingsNotice) {
        if (settingsNotice != null) settingsNoticeDialog.show() else settingsNoticeDialog.hide()
    }

    LaunchedEffect(home.systemStatus.isFullFeatured, home.systemInfo.susfsEnabled, home.systemStatus.isRootAvailable) {
        navigator.closeUnavailable(home.systemStatus.isFullFeatured, home.systemInfo.susfsEnabled, home.systemStatus.isRootAvailable)
    }
    ActivityResumeEffect(homeViewModel) {
        homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = true))
        superUserViewModel.dispatch(SuperUserUiAction.LoadInitialData)
        settingsViewModel.dispatch(SettingsUiAction.RefreshLanguage)
    }
    // Restored detail/filter/search destinations also need the process-local log repository.
    val logsActive = navigator.backStack.any { it == WearRoute.Logs || it == WearRoute.LogSearchResults }
    ActivityResumeEffect(sulogViewModel, logsActive) {
        if (logsActive) sulogViewModel.dispatch(SulogUiAction.LoadInitialData)
    }
    val logsVisible = navigator.top == WearRoute.Logs || navigator.top == WearRoute.LogSearchResults || navigator.top == WearRoute.LogOptions
    val logLifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(sulogViewModel, logsVisible, logLifecycle) {
        if (logsVisible) logLifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { sulogViewModel.observeLive() }
    }
    LaunchedEffect(sulogViewModel) {
        sulogViewModel.events.collect { event ->
            when (event) {
                is SulogUiEvent.Error -> logError = event.message.ifBlank { resources.getString(R.string.operation_failed) }
                SulogUiEvent.Enabled -> settingsViewModel.dispatch(SettingsUiAction.LoadFeatureSettings)
            }
        }
    }
    val dynamicManagerActive = navigator.backStack.any { it is DynamicManagerRoute }
    val dynamicManagerSucceeded = stringResource(R.string.dynamic_manager_set_success)
    val dynamicManagerCleared = stringResource(R.string.dynamic_manager_disabled_success)
    LaunchedEffect(dynamicManagerActive, dynamicManagerViewModel) {
        if (dynamicManagerActive) {
            dynamicManagerViewModel.dispatch(DynamicManagerUiAction.Load)
            dynamicManagerViewModel.events.collect { event ->
                if (event is DynamicManagerUiEvent.OperationCompleted) {
                    dynamicManagerResult = when {
                        !event.success -> WearResult(operationFailedText, success = false)
                        event.operation == DynamicManagerOperation.Clear -> WearResult(dynamicManagerCleared, success = true)
                        else -> WearResult(dynamicManagerSucceeded, success = true)
                    }
                    if (event.success) homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false))
                }
            }
        } else {
            signatureSize = ""
            signatureHash = ""
            dynamicManagerResult = null
        }
    }
    WearResultDialog(dynamicManagerResult) { dynamicManagerResult = null }
    LaunchedEffect(homeViewModel) {
        homeViewModel.events.collect { event -> if (event is HomeUiEvent.Error) homeError = event.message }
    }
    LaunchedEffect(moduleViewModel, operationFailedText) {
        moduleViewModel.events.collect { event ->
            when (event) {
                is ModuleUiEvent.Error -> moduleError = event.message

                ModuleUiEvent.RefreshCompleted -> moduleError = null

                is ModuleUiEvent.EnabledChanged -> {
                    if (event.successful) {
                        moduleViewModel.dispatch(ModuleUiAction.Refresh())
                    } else {
                        moduleError = operationFailedText
                    }
                }

                is ModuleUiEvent.RemovedChanged -> {
                    if (event.successful) {
                        moduleViewModel.dispatch(ModuleUiAction.Refresh())
                    } else {
                        moduleError = operationFailedText
                    }
                }
            }
        }
    }
    LaunchedEffect(settingsViewModel, resources) {
        settingsViewModel.events.collect { event ->
            when (event) {
                is SettingsUiEvent.Error -> {
                    settingsError = event.message
                    settingsMessageResource = null
                }

                is SettingsUiEvent.Message -> {
                    settingsError = null
                    settingsMessageResource = null
                    val message = if (event.formatArg == null) resources.getString(event.stringResource) else resources.getString(event.stringResource, event.formatArg)
                    when (event.stringResource) {
                        R.string.wear_theme_color_applied -> {
                            toast.show(message)
                            if (navigator.top is WearRoute.CustomColor || navigator.top == WearRoute.Color) back()
                        }

                        R.string.wear_invalid_color -> toast.show(message)

                        R.string.dpi_applied_success,
                        R.string.icon_switched,
                        R.string.background_set_success,
                        R.string.background_removed,
                        ->
                            toast.show(message)

                        R.string.background_crop_failed, R.string.settings_selinux_hide_failed -> settingsError = message

                        else -> settingsMessageResource = event.stringResource to event.formatArg
                    }
                }

                SettingsUiEvent.RestartActivity -> activity?.recreate()
            }
        }
    }

    // A found manager update is offered in a dialog, the stable channel first as on the phone's Home.
    WearManagerUpdateDialog(home.stableManagerUpdate ?: home.betaManagerUpdate)
    WearErrorDialog(settingsError?.ifBlank { operationFailedText }) { clearSettingsFeedback() }
    WearModuleInstallConfirmDialog(installer)
    // Keep launch actions queued until the root-dependent navigation has its initial state.
    if (home.isInitialDataLoaded) {
        WearIntentDispatcher(intentChannel, navigator, installer)
    }
    val viewModelStores = rememberViewModelStoreProvider()
    if (WearRoute.OnlineTemplates in navigator.backStack) {
        WearOnlineTemplateFeedback(onlineTemplateViewModel(viewModelStores))
    }
    WearTimeTitleLayout(navigator.top to mainTitle) {
        AppScaffold(
            timeText = { WearTimeText(stringResource((navigator.top ?: WearRoute.Main).titleResource(mainTitle))) },
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Box(Modifier.fillMaxSize()) {
                // Keep the scene background opaque for predictive-back scrims. Each navigation entry
                // renders its own selected backdrop inside the animated scene.
                val hostScheme = MaterialTheme.colorScheme
                MaterialTheme(colorScheme = hostScheme) {
                    NavDisplay(
                        modifier = Modifier.fillMaxSize().then(if (showLinkLoading && themeConfig.isEnableBlur) Modifier.blur(12.dp) else Modifier),
                        backStack = navigator.backStack.filterNot { it is WearRoute.Operations },
                        onBack = { back() },
                        sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy(isUserSwipeEnabled = Build.VERSION.SDK_INT >= 36 || settings.enableSwipeDismiss)),
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(viewModelStores),
                            PageBaseDecorator,
                        ),
                        entryProvider = withPageTransitions(
                            entryProvider {
                                entry<WearRoute.Main> {
                                    WearMainPages(
                                        home = home,
                                        superuser = superuser,
                                        modules = modules,
                                        settings = settings,
                                        preferences = preferences,
                                        homeError = homeError,
                                        superuserError = superuserError,
                                        moduleError = moduleError,
                                        onHome = { action ->
                                            if (action is HomeUiAction.Refresh) homeError = null
                                            homeViewModel.dispatch(action)
                                        },
                                        onSuperUser = superUserViewModel::dispatch,
                                        onModule = { action ->
                                            if (action is ModuleUiAction.Refresh) moduleError = null
                                            moduleViewModel.dispatch(action)
                                        },
                                        onSettings = dispatchSettings,
                                        onOpen = ::open,
                                        onInstall = {
                                            if (home.canOpenPatchDirectly()) navigator.open(WearRoute.LkmInstall, immediately = true) else open(WearRoute.KernelInstall)
                                        },
                                        onTitleChange = { mainTitle = it },
                                        onWebUi = { linkViewModel.openWebUi(it.id, it.name) },
                                    )
                                }
                                entry<WearRoute.Module> { route ->
                                    WearModuleDetail(
                                        module = modules.moduleList.firstOrNull { it.id == route.id },
                                        artwork = modules.moduleIcons[route.id],
                                        error = moduleError,
                                        onBack = ::back,
                                        onEnabledChange = { id, enabled -> moduleViewModel.dispatch(ModuleUiAction.SetEnabled(id, enabled)) },
                                        onRemove = { id, removed -> moduleViewModel.dispatch(ModuleUiAction.SetRemoved(id, removed)) },
                                        onWebUi = { linkViewModel.openWebUi(it.id, it.name) },
                                        onExecute = { open(WearRoute.ModuleAction(it.id)) },
                                        onUpdate = { open(WearRoute.ModuleUpdate(it.id)) },
                                        onErrorShown = { moduleError = null },
                                    )
                                }
                                entry<WearRoute.ModuleAction> { route ->
                                    WearExecuteModulePage(route.id, ::back) { moduleViewModel.dispatch(ModuleUiAction.Refresh()) }
                                }
                                entry<WearRoute.ModuleUpdate> { route ->
                                    val module = modules.moduleList.firstOrNull { it.id == route.id }
                                    WearModuleUpdatePage(
                                        module,
                                        ::back,
                                        installer::update,
                                        onChangelog = { open(WearRoute.ModuleChangelog(route.id)) },
                                        findSource = moduleViewModel::findUpdateSource,
                                        artwork = modules.moduleIcons[route.id],
                                    )
                                }
                                entry<WearRoute.ModuleChangelog> { route ->
                                    WearModuleChangelogPage(modules.moduleList.firstOrNull { it.id == route.id }, ::back)
                                }
                                entry<WearRoute.ModuleInstall> { route ->
                                    WearFlashPage(FlashOperation.Module(route.uri), ::back, installer.onInstalled)
                                }
                                entry<WearRoute.ModuleRepo> {
                                    WearModuleRepoPage(
                                        moduleRepoViewModel,
                                        ::back,
                                        { open(WearRoute.Operations(WearOperationsPage.Repository)) },
                                        hasOperationsPanel = true,
                                        isPageLoading = repositoryPageLoading,
                                    ) { open(WearRoute.OnlineModule(it.moduleId, it.repositoryUrl)) }
                                }
                                entry<WearRoute.ModuleRepoSearch> {
                                    WearModuleRepoSearchPage(moduleRepoViewModel, ::back, onSubmit = {
                                        back()
                                        open(WearRoute.ModuleRepoSearchResults)
                                    }) { open(WearRoute.ModuleRepoSort) }
                                }
                                entry<WearRoute.ModuleRepoSearchResults> {
                                    WearModuleRepoPage(
                                        moduleRepoViewModel,
                                        ::back,
                                        { open(WearRoute.Operations(WearOperationsPage.Repository)) },
                                        hasOperationsPanel = true,
                                        isPageLoading = repositoryPageLoading,
                                    ) { open(WearRoute.OnlineModule(it.moduleId, it.repositoryUrl)) }
                                }
                                entry<WearRoute.OnlineModule> { route ->
                                    WearOnlineModulePage(
                                        route.id,
                                        route.repositoryUrl,
                                        ::back,
                                        linkViewModel::open,
                                        { open(WearRoute.OnlineModuleSection(route.id, route.repositoryUrl, it)) },
                                        installer,
                                    )
                                }
                                entry<WearRoute.OnlineModuleSection> { route ->
                                    WearOnlineModuleSectionPage(route.id, route.repositoryUrl, route.section, ::back, installer)
                                }
                                entry<WearRoute.OnlineModuleDownload> { route ->
                                    WearModuleDownloadPage(route) { installer.downloaded(route, it) }
                                }
                                entry<WearRoute.ModuleSort> {
                                    WearModulePanel(modules, ::back, moduleViewModel::dispatch)
                                }
                                entry<WearRoute.ModuleRepoSort> {
                                    WearModuleRepoSortPage(moduleRepoViewModel, ::back)
                                }
                                entry<WearRoute.Downloads> {
                                    WearDownloadsPage(::back, installer::confirm)
                                }
                                entry<WearRoute.RepositorySources> {
                                    WearRepositorySourcesPage(::back, { open(WearRoute.Operations(WearOperationsPage.RepositorySources)) }) { open(WearRoute.RepositorySource(it)) }
                                }
                                entry<WearRoute.AddRepository> {
                                    WearAddRepositoryPage(::back)
                                }
                                entry<WearRoute.RepositorySourceSearch> {
                                    val query = navigator.backStack.filterIsInstance<WearRoute.RepositorySourceSearchResults>().lastOrNull()?.query.orEmpty()
                                    WearSearchPage(stringResource(R.string.wear_search_repositories), "repository-sources", query, ::back) { input ->
                                        back()
                                        if (navigator.top is WearRoute.Operations) back()
                                        if (navigator.top is WearRoute.RepositorySourceSearchResults) back()
                                        if (input.isNotBlank()) open(WearRoute.RepositorySourceSearchResults(input.trim()))
                                    }
                                }
                                entry<WearRoute.RepositorySourceSearchResults> { route ->
                                    WearRepositorySourcesPage(::back, { open(WearRoute.Operations(WearOperationsPage.RepositorySources)) }, route.query) { open(WearRoute.RepositorySource(it)) }
                                }
                                entry<WearRoute.RepositorySource> { route ->
                                    val catalog by moduleRepoViewModel.state.collectAsStateWithLifecycle()
                                    WearRepositorySourcePage(route.url, catalog.repositoryUrl, ::back) { url ->
                                        moduleRepoViewModel.selectRepository(url)
                                        back()
                                        back()
                                    }
                                }
                                entry<WearRoute.ModuleSearch> {
                                    WearSearchPage(stringResource(R.string.search_modules), "installed-modules", modules.search, ::back, onAdvanced = { open(WearRoute.ModuleSort) }) {
                                        navigator.submitSearch(WearRoute.ModuleSearchResults, it) { query ->
                                            moduleViewModel.dispatch(ModuleUiAction.Search(query))
                                        }
                                    }
                                }
                                entry<WearRoute.ModuleSearchResults> {
                                    WearModuleSearchResults(
                                        modules,
                                        moduleError,
                                        onEdit = moduleSearchInput,
                                        onModuleClick = { open(WearRoute.Module(it)) },
                                        onBack = ::back,
                                    )
                                }
                                // Saved profiles already flow into the app list through ProfileRepository.
                                entry<WearRoute.App> { route ->
                                    val group = superuser.appGroupList.firstOrNull {
                                        it.uid == route.uid && it.primaryPackageName == route.packageName
                                    }
                                    WearAppDetail(
                                        group = group,
                                        isManager = group?.uid?.let { it in superuser.managerUids } == true,
                                        onBack = ::back,
                                        onOpen = ::open,
                                    )
                                }
                                entry<WearRoute.AffectedApps> { route ->
                                    WearAffectedAppsPage(
                                        group = superuser.appGroupList.firstOrNull {
                                            it.uid == route.uid && it.primaryPackageName == route.packageName
                                        },
                                        onBack = ::back,
                                    )
                                }
                                entry<WearRoute.AppActions> { route ->
                                    WearAppActionsRoute(route.uid, route.packageName, onBack = ::back)
                                }
                                entry<WearRoute.AppProfileMode> { route ->
                                    WearAppProfileModeRoute(
                                        route.uid,
                                        route.packageName,
                                        superuser.appGroupList.firstOrNull {
                                            it.uid == route.uid && it.primaryPackageName == route.packageName
                                        }?.mainApp?.label ?: route.packageName,
                                        // A save may finish after Back has already started closing this page.
                                        onBack = { if (navigator.top == route) back() },
                                        onOpen = { destination -> if (navigator.top == route) open(destination) },
                                    )
                                }
                                entry<WearRoute.AppProfileTemplateChoice> { route ->
                                    WearAppProfileTemplateChoiceRoute(
                                        route.uid,
                                        route.packageName,
                                        onBack = { if (navigator.top == route) back() },
                                        templateViewModel = templateViewModel,
                                        isPageLoading = templateChoiceLoading.loading,
                                        isManualRefreshing = templateChoiceLoading.refreshing,
                                        onOpenPanel = { open(WearRoute.Operations(WearOperationsPage.TemplateChoice)) },
                                    )
                                }
                                entry<WearRoute.AppProfileTemplateSearch> { route ->
                                    WearSearchPage(stringResource(R.string.wear_template_search), "profile-templates", route.query, ::back) {
                                        navigator.submitTemplateChoiceSearch(route, it)
                                    }
                                }
                                entry<WearRoute.AppProfileTemplateResults> { route ->
                                    WearAppProfileTemplateChoiceRoute(
                                        route.uid,
                                        route.packageName,
                                        onBack = { if (navigator.top == route) back() },
                                        templateViewModel = templateViewModel,
                                        isPageLoading = templateChoiceLoading.loading,
                                        isManualRefreshing = templateChoiceLoading.refreshing,
                                        onOpenPanel = { open(WearRoute.Operations(WearOperationsPage.TemplateChoice)) },
                                        query = route.query,
                                        onSaved = { navigator.finishTemplateChoice(route) },
                                    )
                                }
                                entry<WearRoute.AppProfileCustom> { route ->
                                    WearAppProfileCustomRoute(
                                        route.uid,
                                        route.packageName,
                                        superuser.appGroupList.firstOrNull {
                                            it.uid == route.uid && it.primaryPackageName == route.packageName
                                        }?.mainApp?.label ?: route.packageName,
                                        onBack = ::back,
                                        templates = templates,
                                        onOpen = ::open,
                                        onNonRootProfile = {
                                            if (navigator.top == route) {
                                                back()
                                                if (navigator.top == WearRoute.AppProfileMode(route.uid, route.packageName)) back()
                                            }
                                        },
                                    )
                                }
                                entry<WearRoute.AppFilter> {
                                    WearSuperUserPanel(superuser, ::back, superUserViewModel::dispatch)
                                }
                                entry<WearRoute.AppSearch> {
                                    WearSearchPage(stringResource(R.string.search_apps), "apps", superuser.search, ::back, onAdvanced = { open(WearRoute.AppFilter) }) {
                                        navigator.submitSearch(WearRoute.AppSearchResults, it) { query ->
                                            superUserViewModel.dispatch(SuperUserUiAction.Search(query))
                                        }
                                    }
                                }
                                entry<WearRoute.AppSearchResults> {
                                    WearAppSearchResults(
                                        superuser,
                                        superuserError,
                                        onEdit = appSearchInput,
                                        onAppClick = { uid, packageName -> open(WearRoute.App(uid, packageName)) },
                                        onBack = ::back,
                                    )
                                }
                                entry<WearRoute.HomeVersionInfo> { WearHomeVersionInfoPage(home, ::back) }
                                entry<WearRoute.HomeStatusInfo> { WearHomeStatusInfoPage(home, ::back) }
                                entry<WearRoute.AllowlistBackup> {
                                    WearAllowlistBackupPage(superUserViewModel, ::back)
                                }
                                entry<WearRoute.Logs> {
                                    WearLogsPage(
                                        state = logs,
                                        loading = sulogViewModel.loading.collectAsStateWithLifecycle().value,
                                        onBack = ::back,
                                        onEnable = {
                                            logError = null
                                            sulogViewModel.dispatch(SulogUiAction.Enable)
                                        },
                                        onRefresh = {
                                            logError = null
                                            sulogViewModel.dispatch(SulogUiAction.Refresh)
                                        },
                                        onOpenPanel = { open(WearRoute.Operations(WearOperationsPage.Logs)) },
                                        onEntry = { open(WearRoute.LogEntry(it.key)) },
                                        error = logError,
                                        hasOperationsPanel = true,
                                    )
                                }
                                entry<WearRoute.LogOptions> {
                                    WearLogOptionsPage(
                                        state = logs,
                                        loading = sulogViewModel.loading.collectAsStateWithLifecycle().value,
                                        onBack = ::back,
                                        onSelectFile = {
                                            logError = null
                                            sulogViewModel.dispatch(SulogUiAction.SelectFile(it))
                                        },
                                        onToggleFilter = { sulogViewModel.dispatch(SulogUiAction.ToggleFilter(it)) },
                                        onClean = {
                                            logError = null
                                            sulogViewModel.dispatch(SulogUiAction.CleanPath(it))
                                        },
                                    )
                                }
                                entry<WearRoute.LogEntry> { route ->
                                    WearLogEntryPage(logs.entries.firstOrNull { it.key == route.key }, ::back)
                                }
                                entry<WearRoute.LogSearch> {
                                    WearSearchPage(stringResource(R.string.sulog_search_placeholder), "sulog", logs.searchText, ::back, onAdvanced = { open(WearRoute.LogOptions) }) {
                                        navigator.submitSearch(WearRoute.LogSearchResults, it) { query ->
                                            sulogViewModel.dispatch(SulogUiAction.Search(query))
                                        }
                                    }
                                }
                                entry<WearRoute.LogSearchResults> {
                                    WearLogsPage(
                                        state = logs,
                                        loading = sulogViewModel.loading.collectAsStateWithLifecycle().value,
                                        onBack = ::back,
                                        onEnable = {
                                            logError = null
                                            sulogViewModel.dispatch(SulogUiAction.Enable)
                                        },
                                        onRefresh = {
                                            logError = null
                                            sulogViewModel.dispatch(SulogUiAction.Refresh)
                                        },
                                        onEntry = { open(WearRoute.LogEntry(it.key)) },
                                        onEditSearch = logSearchInput,
                                        onOpenPanel = { open(WearRoute.Operations(WearOperationsPage.Logs)) },
                                        hasOperationsPanel = true,
                                        error = logError,
                                    )
                                }
                                entry<WearRoute.Settings> { route ->
                                    ActivityResumeEffect(settingsViewModel) {
                                        settingsViewModel.dispatch(SettingsUiAction.LoadFeatureSettings)
                                    }
                                    WearSettingsPage(
                                        settings,
                                        home.systemStatus,
                                        dispatchSettings,
                                        ::open,
                                        ::back,
                                        route.category,
                                        preferences,
                                    )
                                }
                                entry<WearRoute.Selection> { route ->
                                    WearSelectionPage(
                                        route.selection,
                                        settings,
                                        preferences,
                                        preferenceViewModel,
                                        ::back,
                                        dispatchSettings,
                                    )
                                }
                                entry<WearRoute.CustomColor> { route ->
                                    WearCustomColorPage(route.initial, settings, dispatchSettings)
                                }
                                entry<WearRoute.Color> {
                                    WearColorPage(settings, ::back, dispatchSettings, ::open) {
                                        runCatching { selectImageLauncher.launch(arrayOf("image/*")) }
                                            .onFailure { settingsError = operationFailedText }
                                    }
                                }
                                entry<WearRoute.Dpi> {
                                    WearDpiPage(settings, ::back, dispatchSettings, ::open, preferences, preferenceViewModel, home.isSimpleMode) {
                                        homeViewModel.dispatch(HomeUiAction.SetSimpleMode(it))
                                    }
                                }
                                entry<WearRoute.Templates> {
                                    WearTemplatePage(
                                        templates.copy(isLoading = templateManagementLoading.loading, isRefreshing = false),
                                        ::back,
                                        { open(WearRoute.Operations(WearOperationsPage.Templates)) },
                                        { id, readOnly, creation -> open(WearRoute.TemplateEditor(id, readOnly, creation)) },
                                        { templateViewModel.dispatch(TemplateUiAction.Delete(it)) },
                                    )
                                }
                                entry<WearRoute.TemplateSearch> {
                                    WearSearchPage(stringResource(R.string.wear_template_search), "profile-templates", templates.search, ::back) {
                                        navigator.submitTemplateSearch(it) { query -> templateViewModel.dispatch(TemplateUiAction.Search(query)) }
                                    }
                                }
                                entry<WearRoute.TemplateSearchResults> {
                                    WearTemplatePage(
                                        templates.copy(isLoading = templateManagementLoading.loading, isRefreshing = false),
                                        ::back,
                                        { open(WearRoute.Operations(WearOperationsPage.Templates)) },
                                        { id, readOnly, creation -> open(WearRoute.TemplateEditor(id, readOnly, creation)) },
                                        { templateViewModel.dispatch(TemplateUiAction.Delete(it)) },
                                        searchResults = true,
                                    )
                                }
                                entry<WearRoute.OnlineTemplates>(clazzContentKey = { OnlineTemplatesContentKey }) {
                                    val menu = navigator.operationsFor(WearRoute.OnlineTemplates)
                                    WearOnlineTemplatePage(
                                        viewModel = onlineTemplateViewModel(viewModelStores),
                                        onOpenTemplate = { open(WearRoute.OnlineTemplateDetail(it)) },
                                        onBack = ::back,
                                        onOpenPanel = { open(WearRoute.Operations(WearOperationsPage.OnlineTemplates)) },
                                        menuOpen = menu != null,
                                        menuActive = menu != null && navigator.top == menu,
                                        onClosePanel = { if (menu != null && navigator.top == menu) back() },
                                        onSearch = { open(WearRoute.OnlineTemplateSearch) },
                                    )
                                }
                                entry<WearRoute.OnlineTemplateSearch> {
                                    val viewModel = onlineTemplateViewModel(viewModelStores)
                                    val state by viewModel.state.collectAsStateWithLifecycle()
                                    WearSearchPage(stringResource(R.string.wear_template_search), "online-templates", state.search, ::back) { query ->
                                        back()
                                        if (navigator.top is WearRoute.Operations) back()
                                        viewModel.dispatch(OnlineTemplateUiAction.Search(query.trim()))
                                        if (query.isBlank()) {
                                            if (navigator.top == WearRoute.OnlineTemplateSearchResults) back()
                                        } else if (navigator.top != WearRoute.OnlineTemplateSearchResults) {
                                            open(WearRoute.OnlineTemplateSearchResults)
                                        }
                                    }
                                }
                                entry<WearRoute.OnlineTemplateSearchResults> {
                                    val menu = navigator.operationsFor(WearRoute.OnlineTemplateSearchResults)
                                    WearOnlineTemplatePage(
                                        viewModel = onlineTemplateViewModel(viewModelStores),
                                        onOpenTemplate = { open(WearRoute.OnlineTemplateDetail(it)) },
                                        onBack = ::back,
                                        onOpenPanel = { open(WearRoute.Operations(WearOperationsPage.OnlineTemplates)) },
                                        menuOpen = menu != null,
                                        menuActive = menu != null && navigator.top == menu,
                                        onClosePanel = { if (menu != null && navigator.top == menu) back() },
                                        onSearch = { open(WearRoute.OnlineTemplateSearch) },
                                        searchResults = true,
                                    )
                                }
                                entry<WearRoute.OnlineTemplateDetail> { route ->
                                    WearOnlineTemplateDetail(onlineTemplateViewModel(viewModelStores), route.id, ::back)
                                }
                                entry<WearRoute.TemplateEditor> { route ->
                                    WearTemplateEditor(route.id, route.readOnly, route.creation, ::back)
                                }
                                entry<WearRoute.SuSFS> {
                                    WearSuSFSPage(::back) { open(WearRoute.Reboot) }
                                }
                                entry<WearRoute.DynamicManager>(metadata = DynamicManagerTransitions) {
                                    WearDynamicManagerPage(dynamicManager, ::back, ::open) {
                                        dynamicManagerViewModel.dispatch(DynamicManagerUiAction.Clear)
                                    }
                                }
                                entry<WearRoute.DynamicManagerManual>(metadata = DynamicManagerTransitions) {
                                    WearDynamicManagerManualPage(dynamicManager, signatureSize, signatureHash, ::back, ::open) { size, hash ->
                                        dynamicManagerViewModel.dispatch(DynamicManagerUiAction.SetManual(size, hash))
                                    }
                                }
                                entry<WearRoute.DynamicManagerApps>(metadata = DynamicManagerTransitions) {
                                    WearDynamicManagerAppsPage(dynamicManager, ::back, { open(WearRoute.Operations(WearOperationsPage.DynamicManagerApps)) }, { dynamicManagerViewModel.dispatch(DynamicManagerUiAction.Retry) }) {
                                        dynamicManagerViewModel.dispatch(DynamicManagerUiAction.SelectApp(it))
                                    }
                                }
                                entry<WearRoute.DynamicManagerSize>(metadata = DynamicManagerTransitions) {
                                    WearTextInputPage(stringResource(R.string.signature_size), signatureSize) {
                                        signatureSize = it
                                        back()
                                    }
                                }
                                entry<WearRoute.DynamicManagerHash>(metadata = DynamicManagerTransitions) {
                                    WearTextInputPage(stringResource(R.string.signature_hash), signatureHash) {
                                        signatureHash = it
                                        back()
                                    }
                                }
                                entry<WearRoute.DynamicManagerSearch>(metadata = DynamicManagerTransitions) {
                                    WearSearchPage(stringResource(R.string.search_apps), "dynamic-manager", dynamicManager.search, ::back) {
                                        dynamicManagerViewModel.dispatch(DynamicManagerUiAction.Search(it))
                                        back()
                                        if (navigator.top == WearRoute.Operations(WearOperationsPage.DynamicManagerApps)) back()
                                    }
                                }
                                entry<WearRoute.AddUmount> { WearAddUmountPage(::back, umountViewModel) }
                                entry<WearRoute.Umount> {
                                    WearUmountPage(umountViewModel, ::back, { open(WearRoute.Operations(WearOperationsPage.Umount)) })
                                }
                                entry<WearRoute.UmountSearch> {
                                    val query = navigator.backStack.filterIsInstance<WearRoute.UmountSearchResults>().lastOrNull()?.query.orEmpty()
                                    WearSearchPage(stringResource(R.string.wear_search_umount_paths), "umount", query, ::back) { input ->
                                        back()
                                        if (input.isNotBlank()) open(WearRoute.UmountSearchResults(input.trim()))
                                    }
                                }
                                entry<WearRoute.UmountSearchResults> { route ->
                                    WearUmountPage(umountViewModel, ::back, { open(WearRoute.Operations(WearOperationsPage.Umount)) }, route.query)
                                }
                                entry<WearRoute.Uninstall> {
                                    WearUninstallPage(::back) { restore -> open(WearRoute.UninstallFlash(restore)) }
                                }
                                entry<WearRoute.UninstallFlash> { route ->
                                    WearFlashPage(if (route.restore) FlashOperation.Restore else FlashOperation.Uninstall, ::back) {
                                        homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false))
                                    }
                                }
                                entry<WearRoute.KernelInstall> {
                                    WearInstallPage(::back, ::open) {
                                        if (navigator.top == WearRoute.KernelInstall) navigator.replace(WearRoute.LkmInstall)
                                    }
                                }
                                entry<WearRoute.LkmInstall> {
                                    WearLkmInstallPage(::back, ::open)
                                }
                                entry<WearRoute.Ak3Install> { route ->
                                    WearAk3InstallPage(::back, ::open, route.uri)
                                }
                                entry<WearRoute.BootFlash> { route ->
                                    WearFlashPage(route.toOperation(), ::back) {
                                        homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false))
                                    }
                                }
                                entry<WearRoute.KernelFlash> { route ->
                                    WearKernelFlashPage(route.uri, route.slot, route.skipKsud, ::back)
                                }
                                entry<WearRoute.Reboot> {
                                    WearRebootPanel(home.systemStatus.isRootAvailable, ::back) {
                                        homeViewModel.dispatch(HomeUiAction.Reboot(it))
                                    }
                                }
                                entry<WearRoute.Bugreport> {
                                    WearBugreportPage(::back)
                                }
                                entry<WearRoute.About> {
                                    WearAboutDetail(
                                        onBack = ::back,
                                        onOpenLink = linkViewModel::openAboutLink,
                                        onLicenses = { open(WearRoute.Licenses) },
                                    )
                                }
                                entry<WearRoute.Licenses> {
                                    WearLicensePage(::back) { open(WearRoute.LicenseDetail(it)) }
                                }
                                entry<WearRoute.LicenseDetail> { route ->
                                    WearLicenseDetailPage(route.index, ::back, linkViewModel::open)
                                }
                                entry<WearRoute.Browser> { route ->
                                    WearBrowserPage(
                                        route.url,
                                        onBack = ::back,
                                        onOpened = { linkViewModel.webViewOpened(route.url) },
                                        onUnavailable = {
                                            linkViewModel.navigateWithinLink { back() }
                                            linkViewModel.webViewFailed(route.url)
                                        },
                                    )
                                }
                            },
                            immediateEntry = navigator.immediateEntry,
                            pageContent = page@{ key, content ->
                                val route = key as WearRoute
                                // The cloud entry owns its ViewModel and refresh menu, so opening a local menu never loads it.
                                if (route == WearRoute.OnlineTemplates || route == WearRoute.OnlineTemplateSearchResults) {
                                    content()
                                    return@page
                                }
                                val menu = navigator.operationsFor(route)
                                val operationsPage = menu?.page ?: when (route) {
                                    WearRoute.Main -> when (mainTitle) {
                                        R.string.superuser -> WearOperationsPage.SuperUser
                                        R.string.module -> WearOperationsPage.Modules
                                        R.string.home -> WearOperationsPage.Home
                                        else -> null
                                    }

                                    WearRoute.Logs, WearRoute.LogSearchResults -> WearOperationsPage.Logs

                                    WearRoute.Umount, is WearRoute.UmountSearchResults -> WearOperationsPage.Umount

                                    WearRoute.ModuleRepo, WearRoute.ModuleRepoSearchResults -> WearOperationsPage.Repository

                                    WearRoute.RepositorySources, is WearRoute.RepositorySourceSearchResults -> WearOperationsPage.RepositorySources

                                    WearRoute.DynamicManagerApps -> WearOperationsPage.DynamicManagerApps

                                    WearRoute.Templates, WearRoute.TemplateSearchResults -> WearOperationsPage.Templates

                                    is WearRoute.AppProfileTemplateChoice, is WearRoute.AppProfileTemplateResults -> WearOperationsPage.TemplateChoice

                                    else -> null
                                }
                                // An animation callback may only close the menu that scheduled it.
                                val closeMenu: () -> Unit = { if (menu != null && navigator.top == menu) back() }
                                WearOperationsHost(
                                    menuKey = operationsPage,
                                    menuOpen = menu != null,
                                    menuActive = menu != null && navigator.top == menu,
                                    menuContent = { onClosing ->
                                        if (operationsPage != null) {
                                            WearOperationsPanel(
                                                page = operationsPage,
                                                onBack = closeMenu,
                                                onOpen = ::open,
                                                onImportTemplates = importTemplates,
                                                onExportTemplates = { templateViewModel.dispatch(TemplateUiAction.Export) },
                                                onRefresh = if (operationsPage in listOf(WearOperationsPage.Home, WearOperationsPage.Templates, WearOperationsPage.RepositorySources)) {
                                                    null
                                                } else {
                                                    {
                                                        when (operationsPage) {
                                                            WearOperationsPage.Home, WearOperationsPage.Templates, WearOperationsPage.OnlineTemplates, WearOperationsPage.RepositorySources -> Unit

                                                            WearOperationsPage.DynamicManagerApps -> dynamicManagerViewModel.dispatch(DynamicManagerUiAction.Refresh)

                                                            WearOperationsPage.SuperUser -> superUserViewModel.dispatch(SuperUserUiAction.Refresh)

                                                            WearOperationsPage.Modules -> {
                                                                moduleError = null
                                                                moduleViewModel.dispatch(ModuleUiAction.Refresh(manual = true))
                                                            }

                                                            WearOperationsPage.Logs -> {
                                                                logError = null
                                                                sulogViewModel.dispatch(SulogUiAction.Refresh)
                                                            }

                                                            WearOperationsPage.Repository -> moduleRepoViewModel.refresh(fromPull = true)

                                                            WearOperationsPage.Umount -> umountViewModel.dispatch(UmountManagerUiAction.Refresh())

                                                            WearOperationsPage.TemplateChoice -> templateChoiceLoading.refresh()
                                                        }
                                                    }
                                                },
                                                isRefreshing = when (operationsPage) {
                                                    WearOperationsPage.Templates, WearOperationsPage.OnlineTemplates, WearOperationsPage.RepositorySources -> false
                                                    WearOperationsPage.DynamicManagerApps -> dynamicManager.isLoading || dynamicManager.isRefreshing || dynamicManager.isSubmitting
                                                    WearOperationsPage.TemplateChoice -> templateChoiceLoading.loading || templateChoiceLoading.refreshing
                                                    WearOperationsPage.Home -> home.isRefreshing
                                                    WearOperationsPage.SuperUser -> superuser.isRefreshing
                                                    WearOperationsPage.Modules -> modules.isRefreshing
                                                    WearOperationsPage.Logs -> sulogViewModel.loading.collectAsStateWithLifecycle().value != SulogLoading.NONE || logs.isLoading || logs.isRefreshing || logs.isEnabling
                                                    WearOperationsPage.Repository -> repositoryPageLoading || catalog.isRefreshing
                                                    WearOperationsPage.Umount -> umount.isLoading || umount.isRefreshing
                                                },
                                                onSearch = {
                                                    when (operationsPage) {
                                                        WearOperationsPage.SuperUser -> appSearchInput()

                                                        WearOperationsPage.Modules -> moduleSearchInput()

                                                        WearOperationsPage.Logs -> logSearchInput()

                                                        WearOperationsPage.Home, WearOperationsPage.OnlineTemplates -> Unit

                                                        WearOperationsPage.TemplateChoice -> templateChoice?.let {
                                                            open(WearRoute.AppProfileTemplateSearch(it.uid, it.packageName, (route as? WearRoute.AppProfileTemplateResults)?.query.orEmpty()))
                                                        }

                                                        WearOperationsPage.Templates -> open(WearRoute.TemplateSearch)

                                                        WearOperationsPage.RepositorySources -> open(WearRoute.RepositorySourceSearch)

                                                        WearOperationsPage.DynamicManagerApps -> open(WearRoute.DynamicManagerSearch)

                                                        WearOperationsPage.Repository -> open(WearRoute.ModuleRepoSearch)

                                                        WearOperationsPage.Umount -> open(WearRoute.UmountSearch)
                                                    }
                                                },
                                                onInstall = installer::pickFile,
                                                rootAvailable = home.systemStatus.isRootAvailable,
                                                fullFeatured = home.systemStatus.isFullFeatured,
                                                susfsAvailable = home.systemInfo.susfsEnabled,
                                                onReboot = { homeViewModel.dispatch(HomeUiAction.Reboot(it)) },
                                                onClosing = {
                                                    onClosing()
                                                },
                                            )
                                        }
                                    },
                                    content = content,
                                )
                            },
                        ),
                    )
                }
                WearOperationsBackHandler(operationsOnTop) { back() }
                // Over the pages: waiting for a link to open, which Back cancels, and its result.
                WearLinkHost(linkViewModel, showLinkLoading) { url ->
                    linkViewModel.navigateWithinLink { open(WearRoute.Browser(url)) }
                }
            }
        }
    }
}

private val PageBaseDecorator: NavEntryDecorator<NavKey> =
    NavEntryDecorator { entry ->
        val scheme = MaterialTheme.colorScheme
        val pageScheme = remember(scheme) { scheme.copy(background = Color.Black) }
        MaterialTheme(colorScheme = pageScheme) {
            WearBackground {
                entry.Content()
            }
        }
    }

private val WearPageTransitions: Map<String, Any> = if (Build.VERSION.SDK_INT >= 36) {
    val popTransition = (
        scaleIn(initialScale = 0.9f, animationSpec = tween(easing = LinearEasing)) +
            slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(easing = LinearEasing)) +
            fadeIn(initialAlpha = 0.5f, animationSpec = tween(easing = LinearEasing))
        ) togetherWith
        (
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(easing = LinearEasing)) +
                scaleOut(targetScale = 0.9f, animationSpec = tween(easing = LinearEasing))
            )

    // Keep entry and return in NavDisplay, with inverse endpoints for interruption and cancellation.
    NavDisplay.transitionSpec {
        (
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(easing = LinearEasing)) +
                scaleIn(initialScale = 0.9f, animationSpec = tween(easing = LinearEasing))
            ) togetherWith
            (
                scaleOut(targetScale = 0.9f, animationSpec = tween(easing = LinearEasing)) +
                    slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(easing = LinearEasing)) +
                    fadeOut(targetAlpha = 0.5f, animationSpec = tween(easing = LinearEasing))
                )
    } +
        NavDisplay.popTransitionSpec { popTransition } +
        NavDisplay.predictivePopTransitionSpec { popTransition }
} else {
    NavDisplay.transitionSpec {
        (
            slideInHorizontally(initialOffsetX = { it / 2 }, animationSpec = spring(0.8f, 300f)) +
                scaleIn(initialScale = 0.8f, animationSpec = spring(1f, 500f)) +
                fadeIn(animationSpec = spring(1f, 1500f))
            ) togetherWith
            (
                scaleOut(targetScale = 0.85f, animationSpec = spring(1f, 150f)) +
                    slideOutHorizontally(targetOffsetX = { -it / 2 }, animationSpec = spring(0.8f, 200f)) +
                    fadeOut(targetAlpha = 0f, animationSpec = spring(1f, 1400f))
                )
    }
}

// This group retains its shorter entrance; NavDisplay still owns both navigation directions.
private val DynamicManagerTransitions: Map<String, Any> = if (Build.VERSION.SDK_INT >= 36) {
    NavDisplay.transitionSpec {
        (
            slideInHorizontally(initialOffsetX = { it / 4 }, animationSpec = tween(180)) +
                fadeIn(animationSpec = tween(180))
            ) togetherWith ExitTransition.None
    }
} else {
    emptyMap()
}

/**
 * Attaches each page's transition defaults while allowing explicit entry transitions.
 * A scene's metadata is built from its entry's metadata last. The key and the
 * content key are carried over unchanged, leaving the entry's saveable state and ViewModel scopes
 * as they were.
 */
internal fun <T : Any> withPageTransitions(
    provider: (T) -> NavEntry<T>,
    immediateEntry: T? = null,
    pageContent: @Composable (T, @Composable () -> Unit) -> Unit,
): (T) -> NavEntry<T> = { key ->
    val entry = provider(key)
    val forward = if (key == immediateEntry) NavDisplay.transitionSpec { EnterTransition.None togetherWith ExitTransition.None } else emptyMap()
    NavEntry(key, entry.contentKey, WearPageTransitions + entry.metadata + forward) { pageContent(key) { entry.Content() } }
}

/**
 * The four top-level pages in a horizontal pager; without a full-featured root manager only Home
 * and Settings are shown. Swiping right on the first page leaves the app.
 */
@Composable
private fun WearMainPages(
    home: HomeDashboardState,
    superuser: SuperUserUiState,
    modules: ModuleUiState,
    settings: SettingsUiState,
    preferences: WearPreferences,
    homeError: String?,
    superuserError: String?,
    moduleError: String?,
    onHome: (HomeUiAction) -> Unit,
    onSuperUser: (SuperUserUiAction) -> Unit,
    onModule: (ModuleUiAction) -> Unit,
    onSettings: (SettingsUiAction) -> Unit,
    onOpen: (WearRoute) -> Unit,
    onInstall: () -> Unit,
    onTitleChange: (Int) -> Unit,
    onWebUi: (InstalledModule) -> Unit,
) {
    val activity = LocalActivity.current
    if (!home.isInitialDataLoaded && homeError == null) {
        // The first load keeps the startup spinner until Home has its data.
        LaunchedEffect(Unit) { onHome(HomeUiAction.Refresh(showIndicator = false)) }
        WearLoadingScreen()
        return
    }
    val pages = if (home.systemStatus.isFullFeatured) listOf(HOME, SUPERUSER, MODULES, SETTINGS) else listOf(HOME, SETTINGS)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    // Keep the shown page when root availability adds or removes pages.
    var previousPages by remember { mutableStateOf(pages) }
    LaunchedEffect(pages) {
        if (pages != previousPages) {
            val selectedPage = previousPages.getOrNull(pagerState.currentPage) ?: HOME
            pagerState.scrollToPage(pages.indexOf(selectedPage).coerceAtLeast(0))
            previousPages = pages
        }
    }
    // Lists load once, as on the phone; later changes refresh through their own events and the pull
    // gesture, not on every page visit.
    LaunchedEffect(pagerState.currentPage, home.systemStatus.isFullFeatured) {
        onTitleChange(
            when (pages.getOrNull(pagerState.currentPage)) {
                SUPERUSER -> R.string.superuser
                MODULES -> R.string.module
                SETTINGS -> R.string.settings
                else -> R.string.home
            },
        )
        when (pages.getOrNull(pagerState.currentPage)) {
            HOME -> onHome(HomeUiAction.Refresh(showIndicator = false))
            SUPERUSER -> onSuperUser(SuperUserUiAction.LoadInitialData)
            MODULES -> if ((modules.moduleList.isEmpty() || modules.isNeedRefresh) && !modules.isRefreshing) onModule(ModuleUiAction.Refresh())
        }
    }
    val homeListState = rememberTransformingLazyColumnState()
    val appListState = rememberTransformingLazyColumnState()
    val moduleListState = rememberTransformingLazyColumnState()
    val settingsListState = rememberTransformingLazyColumnState()
    WearSwipeToDismissBox(onDismissed = { activity?.finish() }) { isBackground ->
        if (!isBackground) {
            HorizontalPagerScaffold(
                pagerState = pagerState,
                pageIndicator = {
                    // The indicator belongs to the page; it leaves with it as the panel comes over.
                    val pull = LocalWearPanelPull.current
                    Box(Modifier.graphicsLayer { alpha = 1f - pull.floatValue }) {
                        WearHorizontalPageIndicator(pagerState)
                    }
                },
            ) {
                WearHorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    state = pagerState,
                    flingBehavior = PagerScaffoldDefaults.snapWithSpringFlingBehavior(pagerState),
                    key = { pages[it] },
                ) { page ->
                    AnimatedPage(pageIndex = page, pagerState = pagerState) {
                        when (pages.getOrNull(page)) {
                            HOME -> WearHomePage(
                                home,
                                homeError,
                                { activity?.finish() },
                                { onOpen(WearRoute.Operations(WearOperationsPage.Home)) },
                                homeListState,
                                onInstall = onInstall,
                                onVersionInfo = { onOpen(WearRoute.HomeVersionInfo) },
                                onStatusInfo = { onOpen(WearRoute.HomeStatusInfo) },
                                hasOperationsPanel = true,
                                backToTop = true,
                            )

                            SUPERUSER -> WearSuperUserPage(
                                state = superuser,
                                error = superuserError,
                                onRefresh = { onSuperUser(SuperUserUiAction.Refresh) },
                                onBack = { activity?.finish() },
                                onOpenPanel = { onOpen(WearRoute.Operations(WearOperationsPage.SuperUser)) },
                                hasOperationsPanel = true,
                                listState = appListState,
                                onAppClick = { uid, packageName -> onOpen(WearRoute.App(uid, packageName)) },
                                backToTop = true,
                            )

                            MODULES -> WearModulesPage(
                                state = modules,
                                error = moduleError,
                                onRefresh = { onModule(ModuleUiAction.Refresh(manual = true)) },
                                onOpenPanel = { onOpen(WearRoute.Operations(WearOperationsPage.Modules)) },
                                hasOperationsPanel = true,
                                listState = moduleListState,
                                onModuleClick = { onOpen(WearRoute.Module(it)) },
                                onWebUi = onWebUi,
                                onExecute = { onOpen(WearRoute.ModuleAction(it.id)) },
                            )

                            SETTINGS -> WearSettingsPage(
                                settings,
                                home.systemStatus,
                                onSettings,
                                onOpen,
                                { activity?.finish() },
                                preferences = preferences,
                                listState = settingsListState,
                                backToTop = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun WearRoute.BootFlash.toOperation() = FlashOperation.Boot(
    bootUri = bootUri,
    lkm = when {
        lkmUri != null -> LkmSelection.LkmUri(lkmUri)
        kmi != null -> LkmSelection.KmiString(kmi)
        else -> LkmSelection.KmiNone
    },
    ota = ota,
    partition = partition,
    allowShell = allowShell,
    enableAdb = enableAdb,
    forceBackup = forceBackup,
)

internal const val OnlineTemplatesContentKey = "wear-online-templates"

@Composable
internal fun onlineTemplateViewModel(provider: ViewModelStoreProvider): OnlineTemplateViewModel {
    val owner = rememberViewModelStoreOwner(key = OnlineTemplatesContentKey, provider = provider)
    return koinViewModel(viewModelStoreOwner = owner)
}

/** Only the loaded home state can identify the first-install shortcut. */
internal fun HomeDashboardState.canOpenPatchDirectly(): Boolean = isInitialDataLoaded && systemStatus.ksuVersion == null && systemStatus.kernelVersion.isGKI()
