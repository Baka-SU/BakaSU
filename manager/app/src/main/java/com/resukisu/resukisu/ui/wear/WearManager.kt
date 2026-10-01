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
import com.resukisu.resukisu.ui.component.wear.rememberWearConfirmDialog
import com.resukisu.resukisu.ui.component.wear.rememberWearRemoteInput
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.PagerScaffoldDefaults
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

private val SuperUserPages = setOf("app", "search-app", "search-app-results", "app-filter")
private val ModulePages = setOf("module", "module-action", "module-update", "search-module", "search-module-results", "module-sort")
private val LogSubPages = setOf("log-options", "log-entry", "search-log")

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
        // Search results live on their own page; leaving it clears the query of the main list.
        when (detailType) {
            "search-app-results" -> superUserViewModel.dispatch(SuperUserUiAction.Search(""))
            "search-module-results" -> moduleViewModel.dispatch(ModuleUiAction.Search(""))
        }
        detailType = parents.lastOrNull().orEmpty()
        parents = parents.dropLast(1)
    }
    // Submitting a query leaves the input page and shows the results page, or updates it when the
    // input was opened from there; a blank query only closes the input.
    fun showSearchResults(page: String, query: String, search: (String) -> Unit) {
        goBack()
        if (query.isBlank()) return
        search(query.trim())
        if (detailType != page) openPage(page)
    }
    // App and module searches use the system Wear input; a query opens the results page, or updates
    // it when the input was started from there. The in-app input pages are only the fallback.
    val appSearchInput = rememberWearRemoteInput(stringResource(R.string.search_apps),
        onUnavailable = { openPage("search-app") }) { query ->
        if (query.isNotBlank()) {
            superUserViewModel.dispatch(SuperUserUiAction.Search(query.trim()))
            if (detailType != "search-app-results") openPage("search-app-results")
        }
    }
    val moduleSearchInput = rememberWearRemoteInput(stringResource(R.string.search_modules),
        onUnavailable = { openPage("search-module") }) { query ->
        if (query.isNotBlank()) {
            moduleViewModel.dispatch(ModuleUiAction.Search(query.trim()))
            if (detailType != "search-module-results") openPage("search-module-results")
        }
    }
    // The SU log search uses the system Wear input; the in-app input page is only the fallback.
    val logSearchInput = rememberWearRemoteInput(stringResource(R.string.sulog_search_placeholder),
        onUnavailable = { openPage("search-log") }) { sulogViewModel.dispatch(SulogUiAction.Search(it.trim())) }
    val homeListState = rememberTransformingLazyColumnState()
    val appListState = rememberTransformingLazyColumnState()
    val moduleListState = rememberTransformingLazyColumnState()
    val settingsListState = rememberTransformingLazyColumnState()
    var selectedId by rememberSaveable { mutableStateOf("") }
    var selectedLogKey by rememberSaveable { mutableStateOf("") }
    var installUri by rememberSaveable { mutableStateOf("") }
    // Which operation the "flash" page runs: "uninstall", "restore" or a kernel "boot" patch.
    var flashKind by rememberSaveable { mutableStateOf("uninstall") }
    var kernelFlashRequest by rememberSaveable { mutableIntStateOf(0) }
    val kernelInstallViewModel = koinViewModel<WearKernelInstallViewModel>()
    val kernelInstall by kernelInstallViewModel.state.collectAsStateWithLifecycle()
    var actionRequestId by rememberSaveable { mutableIntStateOf(0) }
    var linkUrl by rememberSaveable { mutableStateOf("") }
    var linkMessage by rememberSaveable { mutableIntStateOf(R.string.operation_failed) }
    // Results of opening a module WebUI on the phone, shown as confirmation dialogs.
    var webUiOnPhone by remember { mutableStateOf(false) }
    var webUiPhoneFailed by remember { mutableStateOf(false) }
    var webUiPhoneFailure by remember { mutableStateOf<WearLinkFailure?>(null) }
    var fileTask by rememberSaveable { mutableStateOf("module") }
    var pickerRequest by rememberSaveable { mutableIntStateOf(0) }
    var savedPath by rememberSaveable { mutableStateOf("") }
    var installRequestId by rememberSaveable { mutableIntStateOf(0) }
    var moduleError by remember { mutableStateOf<String?>(null) }
    var superuserError by remember { mutableStateOf<String?>(null) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var logError by remember { mutableStateOf<String?>(null) }
    // As on the phone, a selected module ZIP is installed only after the user confirms it.
    var pendingInstallUri by rememberSaveable { mutableStateOf("") }
    val pendingInstallName = Uri.parse(pendingInstallUri).lastPathSegment?.substringAfterLast('/') ?: pendingInstallUri
    val installDialog = rememberWearConfirmDialog(stringResource(R.string.confirm_installation),
        stringResource(R.string.confirm_install_module_title, pendingInstallName)) {
        installUri = pendingInstallUri
        installRequestId++
        openPage("install")
    }
    fun onFileSelected(uri: String) {
        when (fileTask) {
            "image" -> settingsViewModel.dispatch(SettingsUiAction.SetCustomBackground(uri))
            "boot" -> kernelInstallViewModel.setBootImage(uri)
            "lkm" -> kernelInstallViewModel.setLkm(uri)
            "ak3" -> kernelInstallViewModel.setAk3(uri)
            else -> {
                pendingInstallUri = uri
                installDialog.show()
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

    // Lists load once, as on the phone: later changes refresh through their own events (status
    // changes, module install and toggles) and the pull gesture, not on every page visit. Pages
    // restored on their own after process death still start the first load they need.
    LaunchedEffect(pagerState.currentPage, detailType) {
        val page = if (detailType.isEmpty()) pagerState.currentPage else -1
        if (page == HOME) {
            homeError = null
            homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false))
        }
        if ((page == SUPERUSER || detailType in SuperUserPages) && superuser.isLoading) {
            superUserViewModel.dispatch(SuperUserUiAction.Refresh)
        }
        if ((page == MODULES || detailType in ModulePages) && (modules.moduleList.isEmpty() || modules.isNeedRefresh)) {
            moduleViewModel.dispatch(ModuleUiAction.Refresh())
        }
    }
    // Opening the SU log shows the latest file; returning from its own sub-pages keeps the file
    // chosen there instead of switching back and reading it again.
    var lastDetailType by remember { mutableStateOf("") }
    LaunchedEffect(detailType) {
        if (detailType == "logs" && lastDetailType !in LogSubPages) sulogViewModel.dispatch(SulogUiAction.RefreshLatest)
        lastDetailType = detailType
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
            is WearLinkEvent.Failed -> { linkMessage = linkFailureMessage(event.reason); openPage("link-result") }
            WearLinkEvent.WebUiOnPhone -> webUiOnPhone = true
            is WearLinkEvent.WebUiPhoneFailed -> { webUiPhoneFailure = event.reason; webUiPhoneFailed = true }
        } }
    }
    // A found manager update is offered in a dialog, the stable channel first as on the phone's Home.
    com.resukisu.resukisu.ui.component.wear.WearManagerUpdateDialog(home.stableManagerUpdate ?: home.betaManagerUpdate)
    WearPhoneWebUiDialogs(webUiOnPhone, { webUiOnPhone = false }, webUiPhoneFailure, webUiPhoneFailed) { webUiPhoneFailed = false }
    AppScaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        if (linkLoading) {
            // A waiting spinner; the branded loading screen is only shown at app startup.
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                androidx.wear.compose.material3.CircularProgressIndicator()
            }
        } else if (detailType.isNotEmpty()) {
            com.resukisu.resukisu.ui.component.wear.WearSwipeToDismissBox(onDismissed = { goBack() }) { isBackground ->
                if (isBackground) {
                    // The revealed layer shows only the app backdrop while swiping.
                    Unit
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
                        onSaved = { superUserViewModel.dispatch(SuperUserUiAction.StatusChanged) },
                    )
                    "about" -> WearAboutDetail(onBack = { goBack() },
                        onOpenLink = { linkViewModel.open(it, preferences.link) }, onLicenses = { openPage("licenses") })
                    "licenses" -> WearLicensePage({ goBack() }) { linkViewModel.open(it, preferences.link) }
                    "browser" -> WearBrowserPage(linkUrl, { goBack() }) {
                        goBack()
                        // In automatic mode a WebView that cannot be created falls back to the phone.
                        if (preferences.link == "auto") linkViewModel.open(linkUrl, "phone")
                        else { linkMessage = R.string.wear_link_webview_unavailable; openPage("link-result") }
                    }
                    "link-result" -> WearList(onBack = { goBack() }) { spec ->
                        item { WearPageHeader(spec, null, stringResource(R.string.wear_link_mode)) }
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
                    "saved-log" -> WearList(onBack = { goBack() }) { spec ->
                        item { WearPageHeader(spec, null, stringResource(R.string.save_log)) }
                        item { WearInfoCard(spec) { Text(savedPath) } }
                    }
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
                    "dpi" -> WearDpiPage(settings, settingsMessage, { goBack() }, dispatchSettings,
                        preferences.shape) { openPage("screen-shape") }
                    "picker-mode", "link-mode", "screen-shape", "language", "sucompat" -> WearSelectionPage(
                        detailType, settings, preferences, preferenceViewModel, settingsMessage, { goBack() }, dispatchSettings)
                    "reboot" -> WearRebootPanel(home.systemStatus.isRootAvailable, { goBack() }) {
                        homeViewModel.dispatch(HomeUiAction.Reboot(it))
                    }
                    "app-filter" -> WearSuperUserPanel(superuser, { goBack() }, superUserViewModel::dispatch)
                    "module-sort" -> WearModulePanel(modules, { goBack() }, moduleViewModel::dispatch)
                    "search-app" -> WearTextInputPage(stringResource(R.string.search_apps), superuser.search) {
                        showSearchResults("search-app-results", it) { query -> superUserViewModel.dispatch(SuperUserUiAction.Search(query)) }
                    }
                    "search-module" -> WearTextInputPage(stringResource(R.string.search_modules), modules.search) {
                        showSearchResults("search-module-results", it) { query -> moduleViewModel.dispatch(ModuleUiAction.Search(query)) }
                    }
                    "search-app-results" -> WearAppSearchResults(superuser, superuserError,
                        onEdit = appSearchInput,
                        onAppClick = { uid, packageName -> selectedId = "$uid:$packageName"; openPage("app") },
                        onBack = { goBack() })
                    "search-module-results" -> WearModuleSearchResults(modules, moduleError,
                        onEdit = moduleSearchInput,
                        onModuleClick = { selectedId = it; openPage("module") },
                        onBack = { goBack() })
                    "search-log" -> WearTextInputPage(stringResource(R.string.sulog_search_placeholder), logs.searchText) {
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
                        onSearch = logSearchInput,
                        onClearSearch = { sulogViewModel.dispatch(SulogUiAction.Search("")) },
                        onRefresh = { logError = null; sulogViewModel.dispatch(SulogUiAction.Refresh) },
                        onOptions = { openPage("log-options") },
                        onEntry = { selectedLogKey = it.key; openPage("log-entry") },
                        error = logError,
                    )
                    "log-options" -> WearLogOptionsPage(
                        state = logs,
                        onBack = { goBack() },
                        onSelectFile = { sulogViewModel.dispatch(SulogUiAction.SelectFile(it)) },
                        onToggleFilter = { sulogViewModel.dispatch(SulogUiAction.ToggleFilter(it)) },
                        onClean = { sulogViewModel.dispatch(SulogUiAction.CleanFile) },
                    )
                    "log-entry" -> WearLogEntryPage(logs.entries.firstOrNull { it.key == selectedLogKey }) { goBack() }
                }
                }
            }
        } else if (!home.isInitialDataLoaded && homeError == null) {
            // The manager's first load continues the startup loading screen until Home has its data,
            // which it requests itself in case the pager was restored on another page.
            LaunchedEffect(Unit) { homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false)) }
            com.resukisu.resukisu.ui.component.wear.WearLoadingScreen()
        } else {
            com.resukisu.resukisu.ui.component.wear.WearSwipeToDismissBox(onDismissed = { activity?.finish() }) { isBackground ->
                if (isBackground) {
                    // The revealed layer shows only the app backdrop while swiping.
                    Unit
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
                                            { openPage("reboot") }, homeListState, onInstall = { openPage("kernel-install") }, backToTop = true)
                                        SUPERUSER -> WearSuperUserPage(
                                            state = superuser,
                                            error = superuserError,
                                            onRefresh = {
                                                superuserError = null
                                                superUserViewModel.dispatch(SuperUserUiAction.Refresh)
                                            },
                                            onBack = { activity?.finish() },
                                            onSearch = appSearchInput,
                                            onLogs = { openPage("logs") },
                                            onFilter = { openPage("app-filter") },
                                            listState = appListState,
                                            onAppClick = { uid, packageName ->
                                                selectedId = "$uid:$packageName"
                                                openPage("app")
                                            },
                                            backToTop = true,
                                        )
                                        MODULES -> WearModulesPage(
                                            state = modules,
                                            error = moduleError,
                                            onRefresh = {
                                                moduleError = null
                                                moduleViewModel.dispatch(ModuleUiAction.Refresh(manual = true))
                                            },
                                            onSearch = moduleSearchInput,
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
                                            preferences = preferences, listState = settingsListState, backToTop = true)
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

private fun linkFailureMessage(reason: WearLinkFailure?): Int = when (reason) {
    null -> R.string.operation_failed
    WearLinkFailure.UNSUPPORTED_URL -> R.string.wear_link_unsupported
    WearLinkFailure.WEBVIEW_UNAVAILABLE -> R.string.wear_link_webview_unavailable
    WearLinkFailure.PHONE_UNAVAILABLE -> R.string.wear_link_phone_unavailable
    WearLinkFailure.PHONE_APP_MISSING -> R.string.wear_phone_app_missing
    WearLinkFailure.PHONE_FAILED -> R.string.wear_link_phone_failed
}

/**
 * The official open-on-phone confirmation after a module WebUI was opened on the phone, and the
 * failure confirmation when it could not be: a disconnected phone shows the connection failure icon.
 */
@Composable
private fun WearPhoneWebUiDialogs(opened: Boolean, onOpenedDismissed: () -> Unit, failure: WearLinkFailure?, failed: Boolean, onFailureDismissed: () -> Unit) {
    val openText = OpenOnPhoneDialogDefaults.text
    val openStyle = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(visible = opened, onDismissRequest = onOpenedDismissed,
        curvedText = { openOnPhoneDialogCurvedText(text = openText, style = openStyle) })
    val failureText = stringResource(when (failure) {
        WearLinkFailure.PHONE_UNAVAILABLE -> R.string.wear_phone_disconnected
        WearLinkFailure.PHONE_APP_MISSING -> R.string.wear_phone_app_missing
        else -> R.string.wear_phone_open_failed
    })
    val failureStyle = ConfirmationDialogDefaults.curvedTextStyle
    FailureConfirmationDialog(visible = failed, onDismissRequest = onFailureDismissed,
        curvedText = { confirmationDialogCurvedText(text = failureText, style = failureStyle) }) {
        if (failure == WearLinkFailure.PHONE_UNAVAILABLE) ConfirmationDialogDefaults.ConnectionFailureIcon()
        else ConfirmationDialogDefaults.FailureIcon()
    }
}
