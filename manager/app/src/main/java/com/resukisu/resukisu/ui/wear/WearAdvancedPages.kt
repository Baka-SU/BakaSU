package com.resukisu.resukisu.ui.wear

import com.resukisu.resukisu.ui.component.settings.WearSettingsSwitchWidget

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Fingerprint
import androidx.compose.material.icons.twotone.Flag
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.FormatSize
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.UmountPath
import com.resukisu.resukisu.domain.model.FlashOperation
import com.resukisu.resukisu.ui.component.settings.LazySegmentedColumn
import com.resukisu.resukisu.ui.component.settings.WearSettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.wear.*
import com.resukisu.resukisu.ui.viewmodel.*
import com.resukisu.resukisu.ui.screen.main.UninstallType
import com.resukisu.resukisu.ui.screen.toUmountFlagName
import org.koin.compose.viewmodel.koinViewModel

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearDynamicManagerPage(onBack: () -> Unit) {
    val viewModel = koinViewModel<DynamicManagerViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    val failed = stringResource(R.string.operation_failed)
    val succeeded = stringResource(R.string.dynamic_manager_set_success)
    val cleared = stringResource(R.string.dynamic_manager_disabled_success)
    LaunchedEffect(viewModel) {
        viewModel.dispatch(DynamicManagerUiAction.Refresh)
        viewModel.events.collect { event -> if (event is DynamicManagerUiEvent.OperationCompleted) {
            message = if (!event.success) failed else if (event.operation == DynamicManagerOperation.Clear) cleared else succeeded
        } }
    }
    var input by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableStateOf("") }
    var hash by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<DynamicManagerUiAction?>(null) }
    val grant = rememberWearConfirmDialog(stringResource(R.string.dynamic_manager_grant_confirm_title),
        stringResource(R.string.dynamic_manager_grant_confirm_message)) { pending?.let(viewModel::dispatch); pending = null }
    val clear = rememberWearConfirmDialog(stringResource(R.string.dynamic_manager_clear_confirm_title),
        stringResource(R.string.dynamic_manager_clear_confirm_message)) { viewModel.dispatch(DynamicManagerUiAction.Clear) }
    WearPageTransition(input, if (input.isEmpty()) 0 else 1) { route ->
        if (route.isNotEmpty()) {
            WearSubPage({ input = "" }) {
                WearTextInputPage(stringResource(when (route) {
                    "size" -> R.string.signature_size; "hash" -> R.string.signature_hash; else -> R.string.search_apps
                }), when (route) { "size" -> size; "hash" -> hash; else -> state.search }) {
                    when (route) { "size" -> size = it; "hash" -> hash = it; else -> viewModel.dispatch(DynamicManagerUiAction.Search(it)) }
                    input = ""
                }
            }
        } else {
            val invalidHash = stringResource(R.string.hash_must_be_64_chars)
            WearList(isLoading = state.isLoading || state.isSubmitting, onBack = onBack) { spec ->
                item { WearPageHeader(spec, stringResource(R.string.dynamic_manager_title)) }
                message?.let { item { WearInfoCard(spec) { Text(it) } } }
                item { WearInfoCard(spec) {
                    Text(stringResource(R.string.dynamic_manager_current_status))
                    val config = state.config
                    Text(if (config?.isValid == true) stringResource(R.string.dynamic_manager_enabled_summary, config.size.toString())
                        else stringResource(R.string.dynamic_manager_disabled))
                    if (config?.isValid == true) Text(config.hash)
                } }
                item { WearActionButton(spec, Icons.TwoTone.Search, stringResource(R.string.search_apps), { input = "search" }) }
                LazySegmentedColumn(state.apps, { it.packageName }) { app ->
                    WearSettingsSwitchWidget(spec, app.label, app.isSelected || !app.isChangeable,
                        { if (it) { pending = DynamicManagerUiAction.SelectApp(app); grant.show() } }, app.isChangeable,
                        icon = Icons.TwoTone.Apps)
                }
                item { WearSettingsJumpPageWidget(spec, stringResource(R.string.signature_size), { input = "size" }, icon = Icons.TwoTone.FormatSize) }
                item { WearSettingsJumpPageWidget(spec, stringResource(R.string.signature_hash), { input = "hash" }, icon = Icons.TwoTone.Fingerprint) }
                item { WearActionButton(spec, Icons.TwoTone.Settings, stringResource(R.string.dynamic_manager_manual_config), {
                    val length = size.toIntOrNull()
                    if (length == null || length <= 0) message = failed
                    else if (!hash.matches(Regex("[0-9a-fA-F]{64}"))) message = invalidHash
                    else { pending = DynamicManagerUiAction.SetManual(length, hash); grant.show() }
                }) }
                item { WearActionButton(spec, Icons.TwoTone.Delete, stringResource(R.string.dynamic_manager_clear_config), clear::show) }
            }
        }
    }
}

@Composable
internal fun WearUmountPage(onBack: () -> Unit) {
    val viewModel = koinViewModel<UmountManagerScreenViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<UmountPath?>(null) }
    val remove = rememberWearConfirmDialog(stringResource(R.string.delete), stringResource(R.string.confirm_delete)) {
        selected?.let { viewModel.dispatch(UmountManagerUiAction.Remove(it)) }; selected = null
    }
    LaunchedEffect(viewModel) {
        viewModel.dispatch(UmountManagerUiAction.Refresh())
        viewModel.events.collect { if (it is UmountManagerUiEvent.Message) message = it.message }
    }
    var input by rememberSaveable { mutableStateOf("") }
    var path by rememberSaveable { mutableStateOf("") }
    var flags by rememberSaveable { mutableStateOf("0") }
    WearPageTransition(input, if (input.isEmpty()) 0 else 1) { route ->
        if (route.isNotEmpty()) {
            WearSubPage({ input = "" }) {
                WearTextInputPage(stringResource(if (route == "path") R.string.add_umount_path else R.string.umount_flags),
                    if (route == "path") path else flags) {
                    if (route == "path") path = it else flags = it
                    input = ""
                }
            }
        } else {
            val failed = stringResource(R.string.operation_failed)
            WearList(isLoading = state.isLoading, onBack = onBack) { spec ->
                item { WearPageHeader(spec, stringResource(R.string.umount_path_manager)) }
                message?.let { item { WearInfoCard(spec) { Text(it) } } }
                // A TransformingLazyColumn item places only its last child, so each entry is a single button.
                items(state.umountPaths, key = { it.path }) { entry ->
                    WearActionButton(spec, Icons.TwoTone.Delete, entry.path, { selected = entry; remove.show() },
                        secondaryText = entry.flags.toUmountFlagName())
                }
                item { WearSettingsJumpPageWidget(spec, stringResource(R.string.add_umount_path), { input = "path" },
                    icon = Icons.TwoTone.Folder,
                    description = path.ifEmpty { null }) }
                item { WearSettingsJumpPageWidget(spec, stringResource(R.string.umount_flags), { input = "flags" },
                    icon = Icons.TwoTone.Flag, description = flags) }
                item { WearInfoCard(spec) { Text(stringResource(R.string.umount_flags_hint)) } }
                item { WearActionButton(spec, Icons.TwoTone.Add, stringResource(R.string.add), {
                    val parsed = flags.toIntOrNull()
                    if (!path.startsWith('/') || parsed == null) message = failed
                    else viewModel.dispatch(UmountManagerUiAction.Add(path, parsed))
                }) }
            }
        }
    }
}

@Composable
internal fun WearUninstallPage(onBack: () -> Unit, onStart: (FlashOperation) -> Unit) {
    var selected by remember { mutableStateOf(UninstallType.PERMANENT) }
    val confirm = rememberWearConfirmDialog(stringResource(selected.title), stringResource(selected.message)) {
        onStart(if (selected == UninstallType.PERMANENT) FlashOperation.Uninstall else FlashOperation.Restore)
    }
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.settings_uninstall)) }
        listOf(UninstallType.PERMANENT, UninstallType.RESTORE_STOCK_IMAGE).forEach { option -> item {
            WearActionButton(spec, option.icon, stringResource(option.title), { selected = option; confirm.show() })
        } }
    }
}
