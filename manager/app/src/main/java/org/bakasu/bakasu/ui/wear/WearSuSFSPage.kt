package org.bakasu.bakasu.ui.wear

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText
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
import org.bakasu.bakasu.ui.wear.component.WearDetailField
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearResult
import org.bakasu.bakasu.ui.wear.component.WearResultDialog
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearSubPage
import org.bakasu.bakasu.ui.wear.component.rememberWearConfirmDialog
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.koin.compose.viewmodel.koinViewModel
/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

internal enum class WearSuSFSSection(val title: Int, @DrawableRes val icon: Int) {
    Standard(R.string.susfs_tab_standard, R.drawable.wear_symbol_tune),
    Path(R.string.susfs_tab_sus_path, R.drawable.wear_symbol_folder),
    Kstat(R.string.susfs_tab_sus_kstat, R.drawable.wear_symbol_storage),
    Redirect(R.string.susfs_tab_open_redirect, R.drawable.wear_symbol_alt_route),
    Map(R.string.susfs_tab_sus_map, R.drawable.wear_symbol_map),
}

internal typealias WearSuSFSCommand = (SuSFSCommandReply) -> SuSFSUiAction

/** Wear presentation of the phone SUSFS manager, sharing its commands, config and resources. */
@Composable
internal fun WearSuSFSPage(onBack: () -> Unit, onReboot: () -> Unit) {
    val viewModel = koinViewModel<SuSFSViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf("") }
    var sectionName by rememberSaveable { mutableStateOf(WearSuSFSSection.Standard.name) }
    val section = WearSuSFSSection.valueOf(sectionName)
    var selectedPath by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<WearResult?>(null) }
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

    // Export, import and restoring defaults report [brief] results as curved text; the other
    // commands may report longer messages.
    fun submit(command: WearSuSFSCommand, successMessage: String = succeeded, brief: Boolean = false, onSuccess: () -> Unit = {}) {
        if (busy) return
        busy = true
        result = null
        scope.launch {
            try {
                if (awaitSuSFSBoolean(viewModel, command)) {
                    awaitSuSFSConfig(viewModel) { SuSFSUiAction.Load(it) }
                    result = WearResult(successMessage, success = true, brief)
                    onSuccess()
                } else {
                    result = WearResult(failed, success = false, brief)
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
                result = WearResult(
                    if (commands.size > 1) {
                        batchResult.format(successCount, failCount)
                    } else if (failCount == 0) {
                        succeeded
                    } else {
                        failed
                    },
                    success = failCount == 0,
                )
                if (failCount == 0) page = "section"
            } finally {
                busy = false
            }
        }
    }

    WearResultDialog(result) { result = null }
    ActivityResumeEffect(viewModel) {
        if (!busy) {
            awaitSuSFSConfig(viewModel) { SuSFSUiAction.Refresh(it) }
            awaitSuSFSStatusInfo(viewModel, forceRefresh = true)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it is SuSFSUiEvent.Error) result = WearResult(it.message.ifBlank { failed }, success = false) }
    }
    LaunchedEffect(config?.enabled) {
        if (config?.enabled == false && page in setOf("section", "add", "detail")) page = ""
    }
    val confirmImport = rememberWearConfirmDialog(
        stringResource(R.string.susfs_backup_import_confirm_title),
        stringResource(R.string.susfs_backup_import_confirm_message),
    ) {
        submit({ SuSFSUiAction.ImportConfig(pendingImport, it) }, importSuccess, brief = true)
    }
    val restore = rememberWearConfirmDialog(
        stringResource(R.string.susfs_backup_restore_default),
        stringResource(R.string.susfs_backup_restore_default_desc),
    ) {
        submit({ SuSFSUiAction.RestoreDefault(it) }, succeeded, brief = true)
    }
    fun importSelected(uri: String) {
        pendingImport = uri
        confirmImport.show()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) submit({ SuSFSUiAction.ExportConfig(uri.toString(), it) }, exportSuccess, brief = true)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importSelected(uri.toString())
    }

    // Plain SAF requests, as on the phone; watches without DocumentsUI get the built-in picker.
    fun launchPicker(export: Boolean) {
        runCatching {
            if (export) exportLauncher.launch(backupFileName) else importLauncher.launch(arrayOf("application/json"))
        }.onFailure { result = WearResult(failed, success = false, brief = true) }
    }
    val statusContent: @Composable () -> Unit = {
        WearList(isLoading = state.isLoading || busy, onBack = { page = "" }) { spec ->
            item { WearPageHeader(spec, stringResource(R.string.susfs_tab_status)) }
            item { WearSectionHeader(spec, stringResource(R.string.wear_susfs_status_controls)) }
            item {
                WearSettingsSwitchWidget(
                    spec,
                    stringResource(R.string.susfs_enable_config),
                    config?.enabled == true,
                    { enabled -> submit({ SuSFSUiAction.SetEnabled(enabled, it) }) },
                    enabled = config != null && !busy,
                    icon = R.drawable.wear_symbol_visibility_off,
                    secondaryLabel = stringResource(R.string.susfs_enable_config_summary),
                )
            }
            if (config?.enabled == false) {
                item {
                    WearInfoCard(spec) { Text(stringResource(R.string.susfs_config_disable_warning)) }
                }
            }
            item { WearInfoCard(spec) { WearDetailField(R.drawable.wear_symbol_info, stringResource(R.string.susfs_status_version), state.statusInfo?.version?.ifBlank { noData } ?: noData) } }
            item { WearInfoCard(spec) { WearDetailField(R.drawable.wear_symbol_tune, stringResource(R.string.susfs_status_variant), state.statusInfo?.variant?.ifBlank { noData } ?: noData) } }
            item { WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_status_enabled_features), { page = "capabilities" }, icon = R.drawable.wear_symbol_info) }
            item { WearSectionHeader(spec, stringResource(R.string.wear_susfs_backup_restore)) }
            SegmentedColumn(listOf("export", "import", "reset"), { it }) { action ->
                when (action) {
                    "export" -> WearSettingsJumpPageWidget(
                        spec,
                        stringResource(R.string.susfs_backup_export),
                        { launchPicker(true) },
                        icon = R.drawable.wear_symbol_save,
                        description = stringResource(R.string.susfs_backup_description),
                    )

                    "import" -> WearSettingsJumpPageWidget(
                        spec,
                        stringResource(R.string.susfs_backup_import),
                        { launchPicker(false) },
                        icon = R.drawable.wear_symbol_history,
                        description = stringResource(R.string.susfs_restore_description),
                    )

                    "reset" -> WearSettingsJumpPageWidget(
                        spec,
                        stringResource(R.string.susfs_backup_restore_default),
                        restore::show,
                        icon = R.drawable.wear_symbol_history,
                        description = stringResource(R.string.susfs_backup_restore_default_desc),
                    )
                }
            }
            item { WearSettingsJumpPageWidget(spec, stringResource(R.string.reboot), onReboot, icon = R.drawable.wear_symbol_restart_alt) }
        }
    }
    val route = page
    if (route.isNotEmpty()) {
        WearSubPage({
            page = if (page in setOf("add", "detail")) {
                "section"
            } else if (page == "capabilities") {
                "status"
            } else {
                ""
            }
        }) {
            when (route) {
                "status" -> statusContent()

                "capabilities" -> WearList(onBack = { page = "status" }) { spec ->
                    item { WearPageHeader(spec, stringResource(R.string.susfs_status_enabled_features)) }
                    item { WearInfoCard(spec) { Text(state.statusInfo?.enabledFeatures?.ifBlank { noData } ?: noData) } }
                }

                "section" -> if (config != null && config.enabled) {
                    if (section == WearSuSFSSection.Standard) {
                        WearSuSFSStandardPage(
                            config,
                            busy,
                            viewModel,
                            onBack = { page = "" },
                            onCommand = { command, onSuccess -> submit(command, onSuccess = onSuccess) },
                        )
                    } else {
                        WearSuSFSEntriesPage(
                            section,
                            config,
                            busy,
                            onBack = { page = "" },
                            onAdd = { page = "add" },
                            onSelect = {
                                selectedPath = it
                                page = "detail"
                            },
                        )
                    }
                }

                "add" -> WearSuSFSAddPage(
                    section,
                    busy,
                    viewModel,
                    onBack = { page = "section" },
                    onCommand = ::addEntries,
                )

                "detail" -> if (config != null) {
                    WearSuSFSDetailPage(
                        section,
                        config,
                        selectedPath,
                        busy,
                        onBack = { page = "section" },
                    ) { command -> submit(command) { page = "section" } }
                }
            }
        }
    } else {
        WearList(isLoading = state.isLoading || busy, onBack = onBack) { spec ->
            item { WearPageHeader(spec, stringResource(R.string.susfs_config_title)) }
            item { WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_tab_status), { page = "status" }, icon = R.drawable.wear_symbol_info) }
            LazySegmentedColumn(WearSuSFSSection.entries, { it.name }) { entry ->
                WearSettingsJumpPageWidget(
                    spec,
                    stringResource(entry.title),
                    {
                        sectionName = entry.name
                        page = "section"
                    },
                    icon = entry.icon,
                    enabled = config?.enabled == true,
                )
            }
        }
    }
}
