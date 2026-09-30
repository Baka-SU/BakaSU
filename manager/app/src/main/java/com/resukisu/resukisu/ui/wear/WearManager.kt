package com.resukisu.resukisu.ui.wear

import android.content.Intent
import android.net.Uri
import androidx.wear.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Share
import com.resukisu.resukisu.data.file.WearFileMode
import com.resukisu.resukisu.data.network.WearLinkFailure
import com.resukisu.resukisu.ui.viewmodel.WearFileEvent
import com.resukisu.resukisu.ui.viewmodel.WearFileViewModel
import com.resukisu.resukisu.ui.viewmodel.WearPreferencesViewModel
import com.resukisu.resukisu.ui.viewmodel.WearLinkViewModel
import com.resukisu.resukisu.ui.viewmodel.WearLinkEvent
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearInfoCard
import com.resukisu.resukisu.ui.component.wear.WearActionButton
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import com.resukisu.resukisu.ui.component.wear.WearTextInputPage
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.PagerScaffoldDefaults
import androidx.wear.compose.material3.SwipeToDismissBox
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.viewmodel.HomeUiAction
import com.resukisu.resukisu.ui.viewmodel.HomeUiEvent
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import com.resukisu.resukisu.ui.viewmodel.InstallViewModel
import com.resukisu.resukisu.ui.viewmodel.WearKernelInstallViewModel
import com.resukisu.resukisu.ui.viewmodel.ModuleUiAction
import com.resukisu.resukisu.ui.viewmodel.ModuleUiEvent
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
import com.resukisu.resukisu.ui.viewmodel.SettingsUiAction
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel
import com.resukisu.resukisu.ui.viewmodel.SettingsUiEvent
import com.resukisu.resukisu.ui.viewmodel.SulogUiAction
import com.resukisu.resukisu.ui.viewmodel.SulogViewModel
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiAction
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiEvent
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import org.koin.compose.viewmodel.koinViewModel

private const val HOME = 0
private const val SUPERUSER = 1
private const val MODULES = 2
private const val SETTINGS = 3

@Composable
fun WearManagerScreen() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val homeViewModel = koinViewModel<HomeViewModel>()
    val superUserViewModel = koinViewModel<SuperUserViewModel>()
    val moduleViewModel = koinViewModel<ModuleViewModel>()
    val sulogViewModel = koinViewModel<SulogViewModel>()
    val settingsViewModel = koinViewModel<SettingsViewModel>()
    val preferenceViewModel = koinViewModel<WearPreferencesViewModel>()
    val preferences by preferenceViewModel.state.collectAsStateWithLifecycle()
    val linkViewModel = koinViewModel<WearLinkViewModel>()
    val linkLoading by linkViewModel.loading.collectAsStateWithLifecycle()
    val sendLogText = stringResource(R.string.send_log)
    // Bugreport export/share and system picker detection; the built-in picker page has its own instance.
    val fileViewModel = koinViewModel<WearFileViewModel>(key = "wear-files")
    val fileState by fileViewModel.state.collectAsStateWithLifecycle()

    val home by homeViewModel.uiState.collectAsStateWithLifecycle()
    val superuser by superUserViewModel.uiState.collectAsStateWithLifecycle()
    val modules by moduleViewModel.uiState.collectAsStateWithLifecycle()
    val logs by sulogViewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()

    var detailType by rememberSaveable { mutableStateOf("") }
    var parents by rememberSaveable { mutableStateOf(listOf<String>()) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var settingsMessageResource by remember { mutableStateOf<Pair<Int, Int?>?>(null) }
    // Settings feedback belongs to the page that caused it; navigating away clears it.
    fun clearSettingsFeedback() {
        settingsError = null
        settingsMessageResource = null
    }
    fun openPage(page: String) {
        clearSettingsFeedback()
        parents = parents + detailType
        detailType = page
    }
    fun goBack() {
        clearSettingsFeedback()
        detailType = parents.lastOrNull().orEmpty()
        parents = parents.dropLast(1)
    }
    val homeListState = rememberTransformingLazyColumnState()
    val appListState = rememberTransformingLazyColumnState()
    val moduleListState = rememberTransformingLazyColumnState()
    val settingsListState = rememberTransformingLazyColumnState()
    var selectedId by rememberSaveable { mutableStateOf("") }
    var installUri by rememberSaveable { mutableStateOf("") }
    // Which operation the "flash" page runs: "uninstall", "restore" or a kernel "boot" patch.
    var flashKind by rememberSaveable { mutableStateOf("uninstall") }
    var kernelFlashRequest by rememberSaveable { mutableIntStateOf(0) }
    val kernelInstallViewModel = koinViewModel<WearKernelInstallViewModel>()
    val kernelInstall by kernelInstallViewModel.state.collectAsStateWithLifecycle()
    var actionRequestId by rememberSaveable { mutableIntStateOf(0) }
    var linkUrl by rememberSaveable { mutableStateOf("") }
    var linkMessage by rememberSaveable { mutableIntStateOf(R.string.operation_failed) }
    var fileTask by rememberSaveable { mutableStateOf("module") }
    var pickerRequest by rememberSaveable { mutableIntStateOf(0) }
    var savedPath by rememberSaveable { mutableStateOf("") }
    var installRequestId by rememberSaveable { mutableIntStateOf(0) }
    var moduleError by remember { mutableStateOf<String?>(null) }
    var superuserError by remember { mutableStateOf<String?>(null) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var logError by remember { mutableStateOf<String?>(null) }
    fun onFileSelected(uri: String) {
        when (fileTask) {
            "image" -> settingsViewModel.dispatch(SettingsUiAction.SetCustomBackground(uri))
            "boot" -> kernelInstallViewModel.setBootImage(uri)
            "lkm" -> kernelInstallViewModel.setLkm(uri)
            "ak3" -> kernelInstallViewModel.setAk3(uri)
            else -> {
                installUri = uri
                installRequestId++
                openPage("install")
            }
        }
    }
    val selectModuleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onFileSelected(uri.toString())
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
        if (uri != null) fileViewModel.exportTo(uri.toString())
    }
    val operationFailedText = stringResource(R.string.operation_failed)
    val pickerUnavailableText = stringResource(R.string.wear_file_picker_unavailable)
    fun fileMode(task: String) = when (task) {
        "image" -> WearFileMode.IMAGE
        "bugreport" -> WearFileMode.DIRECTORY
        "boot" -> WearFileMode.BOOT_IMAGE
        "lkm" -> WearFileMode.KERNEL_MODULE
        else -> WearFileMode.ZIP
    }
    fun pickerUnavailable() {
        if (preferences.picker == "auto") openPage("file-picker")
        else { openPage("picker-mode"); settingsError = pickerUnavailableText }
    }
    fun selectFile(task: String) {
        fileTask = task
        pickerRequest++
        if (preferences.picker == "builtin") openPage("file-picker")
        else fileViewModel.checkSystemPicker(fileMode(task))
    }
    fun launchSystemPicker() {
        runCatching {
            if (fileTask == "bugreport") exportLauncher.launch("KernelSU_bugreport.tar.gz")
            else selectModuleLauncher.launch(when (fileTask) {
                "image" -> arrayOf("image/*")
                "lkm" -> arrayOf("application/octet-stream")
                else -> arrayOf("application/zip", "application/octet-stream")
            })
        }.onFailure { pickerUnavailable() }
    }
    val dispatchSettings: (SettingsUiAction) -> Unit = {
        settingsError = null
        settingsMessageResource = null
        settingsViewModel.dispatch(it)
    }
    val settingsMessage = settingsError ?: settingsMessageResource?.let { (id, arg) ->
        if (arg == null) stringResource(id) else stringResource(id, arg)
    }
    val pagerState = rememberPagerState(pageCount = { 4 })
    val pageStateHolder = rememberSaveableStateHolder()

    BackHandler(detailType.isNotEmpty()) { goBack() }
    BackHandler(linkLoading) { linkViewModel.cancel() }

    LaunchedEffect(pagerState.currentPage, detailType) {
        if (detailType.isNotEmpty()) return@LaunchedEffect
        when (pagerState.currentPage) {
            HOME -> {
                homeError = null
                homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false))
            }
            SUPERUSER -> superUserViewModel.dispatch(SuperUserUiAction.Refresh)
            MODULES -> moduleViewModel.dispatch(ModuleUiAction.Refresh())
        }
    }
    LaunchedEffect(detailType) {
        if (detailType == "logs") sulogViewModel.dispatch(SulogUiAction.RefreshLatest)
    }
    LaunchedEffect(sulogViewModel) {
        sulogViewModel.events.collect { event ->
            if (event is com.resukisu.resukisu.ui.viewmodel.SulogUiEvent.Error) logError = event.message
        }
    }
    LaunchedEffect(homeViewModel) {
        homeViewModel.events.collect { event ->
            if (event is HomeUiEvent.Error) homeError = event.message
        }
    }
    LaunchedEffect(superUserViewModel) {
        superUserViewModel.events.collect { event ->
            if (event is SuperUserUiEvent.Error) superuserError = event.message
        }
    }
    LaunchedEffect(moduleViewModel, operationFailedText) {
        moduleViewModel.events.collect { event ->
            when (event) {
                is ModuleUiEvent.Error -> moduleError = event.message
                ModuleUiEvent.RefreshCompleted -> moduleError = null
                is ModuleUiEvent.EnabledChanged -> {
                    if (event.successful) moduleViewModel.dispatch(ModuleUiAction.Refresh())
                    else moduleError = operationFailedText
                }
                is ModuleUiEvent.RemovedChanged -> {
                    if (event.successful) moduleViewModel.dispatch(ModuleUiAction.Refresh())
                    else moduleError = operationFailedText
                }
            }
        }
    }
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.events.collect { event ->
            when (event) {
                is SettingsUiEvent.Error -> {
                    settingsError = event.message
                    settingsMessageResource = null
                }
                is SettingsUiEvent.Message -> {
                    settingsError = null
                    settingsMessageResource = event.stringResource to event.formatArg
                }
                SettingsUiEvent.RestartActivity -> activity?.recreate()
            }
        }
    }

    LaunchedEffect(fileViewModel) {
        fileViewModel.events.collect { event -> when (event) {
            is WearFileEvent.SystemPicker -> if (event.available) launchSystemPicker() else pickerUnavailable()
            is WearFileEvent.Saved -> { savedPath = event.path; openPage("saved-log") }
            is WearFileEvent.Share -> runCatching {
                val uri = Uri.parse(event.uri)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                    .putExtra(Intent.EXTRA_STREAM, uri).setDataAndType(uri, "application/gzip")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), sendLogText))
            }.onFailure { fileViewModel.clearError(); settingsError = operationFailedText }
            is WearFileEvent.Selected -> Unit
        } }
    }
    LaunchedEffect(linkViewModel) {
        linkViewModel.events.collect { event -> when (event) {
            is WearLinkEvent.WebView -> { linkUrl = event.url; openPage("browser") }
            is WearLinkEvent.WebUi -> runCatching {
                context.startActivity(Intent(context, com.resukisu.resukisu.ui.webui.WebUIActivity::class.java)
                    .setData(Uri.parse("kernelsu://webui/${event.moduleId}"))
                    .putExtra("id", event.moduleId).putExtra("name", event.moduleName))
            }.onFailure { moduleError = operationFailedText }
            WearLinkEvent.SentToPhone -> { linkMessage = R.string.wear_phone_sent; openPage("link-result") }
            is WearLinkEvent.Failed -> { linkMessage = linkFailureMessage(event.reason, event.webUi); openPage("link-result") }
        } }
    }
    AppScaffold {
        if (linkLoading) {
            com.resukisu.resukisu.ui.component.wear.WearLoadingScreen()
        } else if (detailType.isNotEmpty()) {
            SwipeToDismissBox(onDismissed = { goBack() }) { isBackground ->
                if (isBackground) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    )
                } else pageStateHolder.SaveableStateProvider("wear-details-$detailType-$selectedId") { when (detailType) {
                    "module" -> WearModuleDetail(
                        module = modules.moduleList.firstOrNull { it.id == selectedId },
                        error = moduleError,
                        onBack = { goBack() },
                        onEnabledChange = { id, enabled ->
                            moduleViewModel.dispatch(ModuleUiAction.SetEnabled(id, enabled))
                        },
                        onRemove = { id, removed ->
                            moduleViewModel.dispatch(ModuleUiAction.SetRemoved(id, removed))
                        },
                        onWebUi = { module -> linkViewModel.openWebUi(module.id, module.name, preferences.link) },
                        onExecute = { actionRequestId++; openPage("module-action") },
                        onUpdate = { openPage("module-update") },
                    )
                    "module-action" -> WearExecuteModulePage(selectedId, actionRequestId, { goBack() }) {
                        moduleViewModel.dispatch(ModuleUiAction.Refresh())
                    }
                    "module-update" -> WearModuleUpdatePage(modules.moduleList.firstOrNull { it.id == selectedId }, { goBack() }) {
                        installUri = it; installRequestId++; goBack(); openPage("install")
                    }
                    "install" -> WearModuleInstallPage(
                        uri = installUri,
                        requestId = installRequestId,
                        onBack = { goBack() },
                        onInstalled = {
                            moduleViewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
                            moduleViewModel.dispatch(ModuleUiAction.Refresh())
                            homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false))
                        },
                    )
                    "app" -> WearAppDetail(
                        group = superuser.appGroupList.firstOrNull {
                            "${it.uid}:${it.primaryPackageName}" == selectedId
                        },
                        isManager = superuser.appGroupList.firstOrNull {
                            "${it.uid}:${it.primaryPackageName}" == selectedId
                        }?.uid?.let { it in superuser.managerUids } == true,
                        onBack = { goBack() },
                    )
                    "about" -> WearAboutDetail(onBack = { goBack() },
                        onOpenLink = { linkViewModel.open(it, preferences.link) }, onLicenses = { openPage("licenses") })
                    "licenses" -> WearLicensePage({ goBack() }) { linkViewModel.open(it, preferences.link) }
                    "browser" -> WearBrowserPage(linkUrl, { goBack() }) {
                        goBack()
                        if (preferences.link == "auto") linkViewModel.open(linkUrl, "auto-external")
                        else { linkMessage = R.string.wear_link_webview_unavailable; openPage("link-result") }
                    }
                    "link-result" -> WearList(onBack = { goBack() }) { spec ->
                        item { WearInfoCard(spec) { Text(stringResource(linkMessage)) } }
                        item { WearActionButton(spec, Icons.TwoTone.Settings, stringResource(R.string.wear_link_mode), { openPage("link-mode") }) }
                    }
                    "file-picker" -> WearFilePage(
                        title = stringResource(when (fileTask) {
                            "image" -> R.string.settings_custom_background
                            "bugreport" -> R.string.save_log
                            "boot" -> R.string.select_file
                            "lkm" -> R.string.install_upload_lkm_file
                            "ak3" -> R.string.horizon_kernel
                            else -> R.string.wear_install_module
                        }),
                        mode = fileMode(fileTask),
                        onBack = { goBack() }, onSelected = { goBack(); onFileSelected(it) },
                        onSaved = { savedPath = it; detailType = "saved-log" },
                        requestId = pickerRequest,
                    )
                    "bugreport" -> WearList(isLoading = fileState.loading, onBack = { goBack() }) { spec ->
                        val fileError = fileState.error ?: if (fileState.failed) operationFailedText else null
                        item { WearPageHeader(spec, null, stringResource(R.string.send_log)) }
                        (fileError ?: settingsMessage)?.let { item { WearInfoCard(spec) { Text(it) } } }
                        item { WearActionButton(spec, Icons.TwoTone.Save, stringResource(R.string.save_log), {
                            settingsError = null; fileViewModel.clearError(); selectFile("bugreport")
                        }) }
                        item { WearActionButton(spec, Icons.TwoTone.Share, sendLogText, {
                            settingsError = null; fileViewModel.share()
                        }) }
                    }
                    "saved-log" -> WearList(onBack = { goBack() }) { spec -> item { WearInfoCard(spec) { Text(savedPath) } } }
                    "settings-general", "settings-security", "settings-advanced", "settings-display" -> WearSettingsPage(
                        settings, home.systemStatus, settingsMessage, dispatchSettings, { openPage(it) }, { goBack() },
                        detailType.removePrefix("settings-"), preferences)
                    "templates" -> WearTemplatePage { goBack() }
                    "dynamic-manager" -> WearDynamicManagerPage { goBack() }
                    "umount" -> WearUmountPage { goBack() }
                    "uninstall" -> WearUninstallPage({ goBack() }) {
                        flashKind = if (it is com.resukisu.resukisu.domain.model.FlashOperation.Restore) "restore" else "uninstall"
                        installRequestId++; openPage("flash")
                    }
                    "flash" -> WearModuleInstallPage("", installRequestId, { goBack() },
                        onInstalled = { homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false)) },
                        operation = when (flashKind) {
                            "boot" -> kernelInstallViewModel.bootOperation(null)
                            "restore" -> com.resukisu.resukisu.domain.model.FlashOperation.Restore
                            else -> com.resukisu.resukisu.domain.model.FlashOperation.Uninstall
                        })
                    "kernel-install", "kernel-install-lkm", "kernel-install-ak3", "install-partition", "install-kmi" -> {
                        val installState by koinViewModel<InstallViewModel>().state.collectAsStateWithLifecycle()
                        val environment = installState.environment
                        val flashBoot = { flashKind = "boot"; installRequestId++; openPage("flash") }
                        when (detailType) {
                            "kernel-install" -> WearInstallPage(environment, installState.loading, { goBack() }) { openPage(it) }
                            "kernel-install-lkm" -> WearLkmInstallPage(environment, kernelInstall, kernelInstallViewModel,
                                { goBack() }, { selectFile("boot") }, { selectFile("lkm") }, { openPage(it) }, flashBoot)
                            "kernel-install-ak3" -> WearAk3InstallPage(environment, kernelInstall, kernelInstallViewModel,
                                { goBack() }, { selectFile("ak3") }) { kernelFlashRequest++; openPage("kernel-flash") }
                            else -> WearKernelChoicePage(detailType, environment, kernelInstall, kernelInstallViewModel,
                                { goBack() }) { goBack(); flashBoot() }
                        }
                    }
                    "kernel-flash" -> WearKernelFlashPage(kernelInstall.ak3Uri.orEmpty(), kernelInstall.slot,
                        kernelInstall.skipKsud, kernelFlashRequest) { goBack() }
                    "color" -> WearColorPage(settings, settingsMessage, { goBack() }, dispatchSettings, { selectFile("image") })
                    "dpi" -> WearDpiPage(settings, settingsMessage, { goBack() }, dispatchSettings)
                    "picker-mode", "link-mode", "language", "sucompat" -> WearSelectionPage(
                        detailType, settings, preferences, preferenceViewModel, settingsMessage, { goBack() }, dispatchSettings)
                    "reboot" -> WearRebootPanel(home.systemStatus.isRootAvailable, { goBack() }) {
                        homeViewModel.dispatch(HomeUiAction.Reboot(it))
                    }
                    "app-filter" -> WearSuperUserPanel(superuser, { goBack() }, superUserViewModel::dispatch)
                    "module-sort" -> WearModulePanel(modules, { goBack() }, moduleViewModel::dispatch)
                    "search-app" -> WearTextInputPage(stringResource(R.string.search_apps), superuser.search, { goBack() }) {
                        superUserViewModel.dispatch(SuperUserUiAction.Search(it)); goBack()
                    }
                    "search-module" -> WearTextInputPage(stringResource(R.string.search_modules), modules.search, { goBack() }) {
                        moduleViewModel.dispatch(ModuleUiAction.Search(it)); goBack()
                    }
                    "search-log" -> WearTextInputPage(stringResource(R.string.sulog_search_placeholder), logs.searchText, { goBack() }) {
                        sulogViewModel.dispatch(SulogUiAction.Search(it)); goBack()
                    }
                    "logs" -> WearLogsPage(
                        state = logs,
                        onBack = { goBack() },
                        onSetEnabled = { enabled ->
                            sulogViewModel.dispatch(
                                if (enabled) SulogUiAction.Enable else SulogUiAction.Disable
                            )
                        },
                        onSelectFile = { path ->
                            sulogViewModel.dispatch(SulogUiAction.SelectFile(path))
                        },
                        onSearch = { openPage("search-log") },
                        onRefresh = { logError = null; sulogViewModel.dispatch(SulogUiAction.Refresh) },
                        onClean = { sulogViewModel.dispatch(SulogUiAction.CleanFile) },
                        onToggleFilter = { sulogViewModel.dispatch(SulogUiAction.ToggleFilter(it)) },
                        error = logError,
                    )
                }
                }
            }
        } else {
            SwipeToDismissBox(onDismissed = { activity?.finish() }) { isBackground ->
                if (isBackground) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    )
                } else {
                    pageStateHolder.SaveableStateProvider("wear-main-pages") {
                        HorizontalPagerScaffold(pagerState = pagerState) {
                            HorizontalPager(
                                modifier = Modifier.fillMaxSize(),
                                state = pagerState,
                                flingBehavior = PagerScaffoldDefaults.snapWithSpringFlingBehavior(pagerState),
                            ) { page ->
                                AnimatedPage(pageIndex = page, pagerState = pagerState) {
                                    when (page) {
                                        HOME -> WearHomePage(home, homeError, { activity?.finish() },
                                            { openPage("reboot") }, homeListState, onInstall = { openPage("kernel-install") })
                                        SUPERUSER -> WearSuperUserPage(
                                            state = superuser,
                                            error = superuserError,
                                            onRefresh = {
                                                superuserError = null
                                                superUserViewModel.dispatch(SuperUserUiAction.Refresh)
                                            },
                                            onBack = { activity?.finish() },
                                            onSearch = { openPage("search-app") },
                                            onLogs = { openPage("logs") },
                                            onFilter = { openPage("app-filter") },
                                            listState = appListState,
                                            onAppClick = { uid, packageName ->
                                                selectedId = "$uid:$packageName"
                                                openPage("app")
                                            },
                                        )
                                        MODULES -> WearModulesPage(
                                            state = modules,
                                            error = moduleError,
                                            onRefresh = {
                                                moduleError = null
                                                moduleViewModel.dispatch(ModuleUiAction.Refresh(manual = true))
                                            },
                                            onSearch = { openPage("search-module") },
                                            onSort = { openPage("module-sort") },
                                            listState = moduleListState,
                                            onModuleClick = { id ->
                                                selectedId = id
                                                openPage("module")
                                            },
                                            onInstallClick = { selectFile("module") },
                                        )
                                        SETTINGS -> WearSettingsPage(settings, home.systemStatus, settingsMessage,
                                            dispatchSettings, { openPage(it) }, { activity?.finish() },
                                            preferences = preferences, listState = settingsListState)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun linkFailureMessage(reason: WearLinkFailure?, webUi: Boolean): Int = when (reason) {
    null -> if (webUi) R.string.wear_webui_local_only else R.string.operation_failed
    WearLinkFailure.UNSUPPORTED_URL -> R.string.wear_link_unsupported
    WearLinkFailure.WEBVIEW_UNAVAILABLE -> R.string.wear_link_webview_unavailable
    WearLinkFailure.BROWSER_UNAVAILABLE -> R.string.wear_link_browser_unavailable
    WearLinkFailure.PHONE_UNAVAILABLE -> R.string.wear_link_phone_unavailable
    WearLinkFailure.PHONE_FAILED -> R.string.wear_link_phone_failed
}
