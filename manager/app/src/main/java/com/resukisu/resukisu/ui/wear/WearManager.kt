package com.resukisu.resukisu.ui.wear

import android.content.pm.PackageManager
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
import com.resukisu.resukisu.ui.viewmodel.ModuleUiAction
import com.resukisu.resukisu.ui.viewmodel.ModuleUiEvent
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
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

    val home by homeViewModel.uiState.collectAsStateWithLifecycle()
    val superuser by superUserViewModel.uiState.collectAsStateWithLifecycle()
    val modules by moduleViewModel.uiState.collectAsStateWithLifecycle()
    val logs by sulogViewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()

    var detailType by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var installRequestId by rememberSaveable { mutableIntStateOf(0) }
    var moduleError by remember { mutableStateOf<String?>(null) }
    var superuserError by remember { mutableStateOf<String?>(null) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var settingsMessageResource by remember { mutableStateOf<Pair<Int, Int?>?>(null) }
    val selectModuleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            selectedId = uri.toString()
            installRequestId++
            detailType = "install"
        }
    }
    val operationFailedText = stringResource(R.string.operation_failed)
    val pickerUnavailableText = stringResource(R.string.wear_file_picker_unavailable)
    val pagerState = rememberPagerState(pageCount = { 4 })
    val pageStateHolder = rememberSaveableStateHolder()

    BackHandler(detailType.isNotEmpty()) { detailType = "" }

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

    AppScaffold {
        if (detailType.isNotEmpty()) {
            SwipeToDismissBox(onDismissed = { detailType = "" }) { isBackground ->
                if (isBackground) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    )
                } else when (detailType) {
                    "module" -> WearModuleDetail(
                        module = modules.moduleList.firstOrNull { it.id == selectedId },
                        error = moduleError,
                        onBack = { detailType = "" },
                        onEnabledChange = { id, enabled ->
                            moduleViewModel.dispatch(ModuleUiAction.SetEnabled(id, enabled))
                        },
                        onRemove = { id, removed ->
                            moduleViewModel.dispatch(ModuleUiAction.SetRemoved(id, removed))
                        },
                    )
                    "install" -> WearModuleInstallPage(
                        uri = selectedId,
                        requestId = installRequestId,
                        onBack = { detailType = "" },
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
                        onBack = { detailType = "" },
                    )
                    "about" -> WearAboutDetail(onBack = { detailType = "" })
                    "logs" -> WearLogsPage(
                        state = logs,
                        onBack = { detailType = "" },
                        onSetEnabled = { enabled ->
                            sulogViewModel.dispatch(
                                if (enabled) SulogUiAction.Enable else SulogUiAction.Disable
                            )
                        },
                        onSelectFile = { path ->
                            sulogViewModel.dispatch(SulogUiAction.SelectFile(path))
                        },
                    )
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
                                        HOME -> WearHomePage(home, homeError)
                                        SUPERUSER -> WearSuperUserPage(
                                            state = superuser,
                                            isRootAvailable = home.systemStatus.isRootAvailable,
                                            error = superuserError,
                                            onRefresh = {
                                                superuserError = null
                                                superUserViewModel.dispatch(SuperUserUiAction.Refresh)
                                            },
                                            onAppClick = { uid, packageName ->
                                                selectedId = "$uid:$packageName"
                                                detailType = "app"
                                            },
                                        )
                                        MODULES -> WearModulesPage(
                                            state = modules,
                                            error = moduleError,
                                            onRefresh = {
                                                moduleError = null
                                                moduleViewModel.dispatch(ModuleUiAction.Refresh(manual = true))
                                            },
                                            onModuleClick = { id ->
                                                selectedId = id
                                                detailType = "module"
                                            },
                                            onInstallClick = {
                                                val mimeTypes = arrayOf(
                                                    "application/zip", "application/octet-stream"
                                                )
                                                val pickers = context.packageManager.queryIntentActivities(
                                                    ActivityResultContracts.OpenDocument()
                                                        .createIntent(context, mimeTypes),
                                                    PackageManager.MATCH_DEFAULT_ONLY,
                                                )
                                                if (pickers.isNotEmpty() &&
                                                    pickers.all { it.activityInfo.name.endsWith("DocumentsStub") }
                                                ) {
                                                    moduleError = pickerUnavailableText
                                                } else {
                                                    moduleError = null
                                                    runCatching { selectModuleLauncher.launch(mimeTypes) }
                                                        .onFailure { moduleError = pickerUnavailableText }
                                                }
                                            },
                                        )
                                        SETTINGS -> WearSettingsPage(
                                            state = settings,
                                            message = settingsError ?: settingsMessageResource?.let { (id, arg) ->
                                                if (arg == null) stringResource(id) else stringResource(id, arg)
                                            },
                                            onAction = {
                                                settingsError = null
                                                settingsMessageResource = null
                                                settingsViewModel.dispatch(it)
                                            },
                                            onLogsClick = { detailType = "logs" },
                                            onAboutClick = { detailType = "about" },
                                        )
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
