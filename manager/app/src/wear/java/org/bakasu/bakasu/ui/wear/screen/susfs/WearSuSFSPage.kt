package org.bakasu.bakasu.ui.wear.screen.susfs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.util.ActivityResumeEffect
import org.bakasu.bakasu.ui.viewmodel.SuSFSCommandReply
import org.bakasu.bakasu.ui.viewmodel.SuSFSUiAction
import org.bakasu.bakasu.ui.viewmodel.SuSFSUiEvent
import org.bakasu.bakasu.ui.viewmodel.SuSFSViewModel
import org.bakasu.bakasu.ui.viewmodel.awaitSuSFSBoolean
import org.bakasu.bakasu.ui.viewmodel.awaitSuSFSConfig
import org.bakasu.bakasu.ui.viewmodel.awaitSuSFSStatusInfo
import org.bakasu.bakasu.ui.wear.WearRoute
import org.bakasu.bakasu.ui.wear.component.WearDetailField
import org.bakasu.bakasu.ui.wear.component.WearFeedbackEffect
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearSubNavigation
import org.bakasu.bakasu.ui.wear.component.rememberWearConfirmDialog
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.bakasu.bakasu.ui.wear.rememberWearNavigator
import org.koin.compose.viewmodel.koinViewModel
/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

internal enum class WearSuSFSSection(val title: Int, val icon: ImageVector) {
    Standard(R.string.susfs_tab_standard, Icons.Rounded.Tune),
    Path(R.string.susfs_tab_sus_path, Icons.Rounded.Folder),
    Kstat(R.string.susfs_tab_sus_kstat, Icons.Rounded.Storage),
    Redirect(R.string.susfs_tab_open_redirect, Icons.Rounded.AltRoute),
    Map(R.string.susfs_tab_sus_map, Icons.Rounded.Map),
}

internal typealias WearSuSFSCommand = (SuSFSCommandReply) -> SuSFSUiAction

@Composable
internal fun WearSuSFSPage(onBack: () -> Unit, onReboot: () -> Unit) {
    val viewModel = koinViewModel<SuSFSViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val navigator = rememberWearNavigator(onExit = onBack, initialRoute = WearRoute.SuSFS)
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingImport by rememberSaveable { mutableStateOf("") }
    val failed = stringResource(R.string.susfs_operation_failed)
    val succeeded = stringResource(R.string.susfs_operation_success)
    val importSuccess = stringResource(R.string.susfs_backup_import_success)
    val exportSuccess = stringResource(R.string.susfs_backup_export_success)
    val noData = stringResource(R.string.susfs_status_no_data)
    val batchResult = stringResource(R.string.susfs_entry_import_success)
    val backupFileName = stringResource(R.string.wear_susfs_backup_file_name)
    val config = state.config

    fun submit(command: WearSuSFSCommand, successMessage: String = succeeded, onSuccess: () -> Unit = {}) {
        if (busy) return
        busy = true
        result = null
        scope.launch {
            try {
                if (awaitSuSFSBoolean(viewModel, command)) {
                    awaitSuSFSConfig(viewModel) { SuSFSUiAction.Load(it) }
                    result = successMessage
                    onSuccess()
                } else {
                    result = failed
                }
            } finally {
                busy = false
            }
        }
    }

    fun addEntries(commands: List<WearSuSFSCommand>) {
        if (busy || commands.isEmpty()) return
        busy = true
        result = null
        scope.launch {
            try {
                var successCount = 0
                commands.forEach { if (awaitSuSFSBoolean(viewModel, it)) successCount++ }
                if (successCount > 0) awaitSuSFSConfig(viewModel) { SuSFSUiAction.Load(it) }
                val failCount = commands.size - successCount
                result = if (commands.size > 1) {
                    batchResult.format(successCount, failCount)
                } else if (failCount == 0) {
                    succeeded
                } else {
                    failed
                }
                if (failCount == 0 && navigator.top is WearRoute.SuSFSAdd) navigator.back()
            } finally {
                busy = false
            }
        }
    }

    WearFeedbackEffect(result) { result = null }
    ActivityResumeEffect(viewModel) {
        if (!busy) {
            awaitSuSFSConfig(viewModel) { SuSFSUiAction.Refresh(it) }
            awaitSuSFSStatusInfo(viewModel, forceRefresh = true)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it is SuSFSUiEvent.Error) result = it.message.ifBlank { failed } }
    }
    LaunchedEffect(config?.enabled) {
        if (config?.enabled == false && navigator.backStack.any { it is WearRoute.SuSFSSection }) {
            while (navigator.backStack.size > 1) navigator.back()
        }
    }
    val confirmImport = rememberWearConfirmDialog(
        stringResource(R.string.susfs_backup_import_confirm_title),
        stringResource(R.string.susfs_backup_import_confirm_message),
    ) {
        submit({ SuSFSUiAction.ImportConfig(pendingImport, it) }, importSuccess)
    }
    val restore = rememberWearConfirmDialog(
        stringResource(R.string.susfs_backup_restore_default),
        stringResource(R.string.susfs_backup_restore_default_desc),
    ) {
        submit({ SuSFSUiAction.RestoreDefault(it) }, succeeded)
    }
    fun importSelected(uri: String) {
        pendingImport = uri
        confirmImport.show()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) submit({ SuSFSUiAction.ExportConfig(uri.toString(), it) }, exportSuccess)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importSelected(uri.toString())
    }

    fun launchPicker(export: Boolean) {
        runCatching {
            if (export) exportLauncher.launch(backupFileName) else importLauncher.launch(arrayOf("application/json"))
        }.onFailure { result = failed }
    }
    val statusContent: @Composable () -> Unit = {
        WearList(isLoading = state.isLoading || busy, onBack = navigator::back) { spec ->
            item { WearPageHeader(spec, stringResource(R.string.susfs_tab_status)) }
            item { WearSectionHeader(spec, stringResource(R.string.wear_susfs_status_controls)) }
            item {
                WearSettingsSwitchWidget(
                    spec,
                    stringResource(R.string.susfs_enable_config),
                    config?.enabled == true,
                    { enabled -> submit({ SuSFSUiAction.SetEnabled(enabled, it) }) },
                    enabled = config != null && !busy,
                    icon = Icons.Rounded.VisibilityOff,
                    secondaryLabel = stringResource(R.string.susfs_enable_config_summary),
                )
            }
            if (config?.enabled == false) {
                item {
                    WearInfoCard(spec) { Text(stringResource(R.string.susfs_config_disable_warning)) }
                }
            }
            item { WearInfoCard(spec) { WearDetailField(Icons.Rounded.Info, stringResource(R.string.susfs_status_version), state.statusInfo?.version?.ifBlank { noData } ?: noData) } }
            item { WearInfoCard(spec) { WearDetailField(Icons.Rounded.Tune, stringResource(R.string.susfs_status_variant), state.statusInfo?.variant?.ifBlank { noData } ?: noData) } }
            item { WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_status_enabled_features), { navigator.open(WearRoute.SuSFSCapabilities) }, icon = Icons.Rounded.Info) }
            item { WearSectionHeader(spec, stringResource(R.string.wear_susfs_backup_restore)) }
            SegmentedColumn(listOf("export", "import", "reset"), { it }) { action ->
                when (action) {
                    "export" -> WearSettingsJumpPageWidget(
                        spec,
                        stringResource(R.string.susfs_backup_export),
                        { launchPicker(true) },
                        icon = Icons.Rounded.Save,
                        description = stringResource(R.string.susfs_backup_description),
                    )

                    "import" -> WearSettingsJumpPageWidget(
                        spec,
                        stringResource(R.string.susfs_backup_import),
                        { launchPicker(false) },
                        icon = Icons.Rounded.History,
                        description = stringResource(R.string.susfs_restore_description),
                    )

                    "reset" -> WearSettingsJumpPageWidget(
                        spec,
                        stringResource(R.string.susfs_backup_restore_default),
                        restore::show,
                        icon = Icons.Rounded.History,
                        description = stringResource(R.string.susfs_backup_restore_default_desc),
                    )
                }
            }
            item { WearSettingsJumpPageWidget(spec, stringResource(R.string.reboot), onReboot, icon = Icons.Rounded.RestartAlt) }
        }
    }
    WearSubNavigation(navigator) { route ->
        when (route) {
            WearRoute.SuSFSStatus -> statusContent()

            WearRoute.SuSFSCapabilities -> WearList(onBack = navigator::back) { spec ->
                item { WearPageHeader(spec, stringResource(R.string.susfs_status_enabled_features)) }
                item { WearInfoCard(spec) { Text(state.statusInfo?.enabledFeatures?.ifBlank { noData } ?: noData) } }
            }

            is WearRoute.SuSFSSection -> if (config != null && config.enabled) {
                val section = WearSuSFSSection.valueOf(route.section)
                if (section == WearSuSFSSection.Standard) {
                    WearSuSFSStandardPage(config, busy, viewModel, navigator::back) { command, onSuccess -> submit(command, onSuccess = onSuccess) }
                } else {
                    WearSuSFSEntriesPage(
                        section,
                        config,
                        busy,
                        navigator::back,
                        onAdd = { navigator.open(WearRoute.SuSFSAdd(route.section)) },
                        onSelect = { navigator.open(WearRoute.SuSFSDetail(route.section, it)) },
                    )
                }
            }

            is WearRoute.SuSFSAdd -> WearSuSFSAddPage(WearSuSFSSection.valueOf(route.section), busy, navigator::back, ::addEntries)

            is WearRoute.SuSFSDetail -> if (config != null) {
                WearSuSFSDetailPage(WearSuSFSSection.valueOf(route.section), config, route.path, busy, navigator::back) { command ->
                    submit(command) { if (navigator.top == route) navigator.back() }
                }
            }

            else -> WearList(isLoading = state.isLoading || busy, onBack = onBack) { spec ->
                item { WearPageHeader(spec, stringResource(R.string.susfs_config_title)) }
                item { WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_tab_status), { navigator.open(WearRoute.SuSFSStatus) }, icon = Icons.Rounded.Info) }
                LazySegmentedColumn(WearSuSFSSection.entries, { it.name }) { entry ->
                    WearSettingsJumpPageWidget(spec, stringResource(entry.title), { navigator.open(WearRoute.SuSFSSection(entry.name)) }, icon = entry.icon, enabled = config?.enabled == true)
                }
            }
        }
    }
}
