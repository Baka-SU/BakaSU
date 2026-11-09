package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import java.util.Locale
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.DynamicManagerApp
import org.bakasu.bakasu.domain.model.UmountPath
import org.bakasu.bakasu.ui.component.PackageIcon
import org.bakasu.bakasu.ui.screen.main.UninstallType
import org.bakasu.bakasu.ui.screen.toUmountFlagName
import org.bakasu.bakasu.ui.viewmodel.DynamicManagerUiState
import org.bakasu.bakasu.ui.viewmodel.UmountManagerScreenViewModel
import org.bakasu.bakasu.ui.viewmodel.UmountManagerUiAction
import org.bakasu.bakasu.ui.viewmodel.UmountManagerUiEvent
import org.bakasu.bakasu.ui.viewmodel.UmountManagerUiState
import org.bakasu.bakasu.ui.viewmodel.managerSourceLabel
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearIconAction
import org.bakasu.bakasu.ui.wear.component.WearInfoRow
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearModuleInfoCard
import org.bakasu.bakasu.ui.wear.component.WearModuleSwipeActions
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearResult
import org.bakasu.bakasu.ui.wear.component.WearResultDialog
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearSubPage
import org.bakasu.bakasu.ui.wear.component.WearTextInputPage
import org.bakasu.bakasu.ui.wear.component.rememberWearConfirmDialog
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun WearDynamicManagerPage(
    state: DynamicManagerUiState,
    onBack: () -> Unit,
    onOpen: (WearRoute) -> Unit,
    onClear: () -> Unit,
) {
    val clear = rememberWearConfirmDialog(
        stringResource(R.string.dynamic_manager_clear_confirm_title),
        stringResource(R.string.dynamic_manager_clear_confirm_message),
        destructive = true,
        onConfirm = onClear,
    )
    WearList(isLoading = state.isLoading || state.isSubmitting, onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.dynamic_manager_title)) }
        item { WearSectionHeader(spec, stringResource(R.string.dynamic_manager_current_status)) }
        item {
            WearModuleInfoCard(spec) {
                WearInfoRow(
                    Icons.Rounded.Settings,
                    stringResource(R.string.wear_manager_status),
                    state.config?.takeIf { it.isValid }?.let { stringResource(R.string.dynamic_manager_enabled_summary, it.size.toString()) }
                        ?: stringResource(R.string.dynamic_manager_disabled),
                    largeIcon = true,
                )
            }
        }
        item {
            WearModuleInfoCard(spec) {
                WearInfoRow(
                    Icons.Rounded.Fingerprint,
                    stringResource(R.string.signature_hash),
                    state.config?.hash?.ifBlank { null } ?: stringResource(R.string.unknown),
                    largeIcon = true,
                )
            }
        }
        item { WearSectionHeader(spec, stringResource(R.string.wear_operations)) }
        item {
            WearSettingsJumpPageWidget(
                spec,
                stringResource(R.string.dynamic_manager_manual_config),
                { onOpen(WearRoute.DynamicManagerManual) },
                icon = Icons.Rounded.Settings,
                description = stringResource(R.string.dynamic_manager_manual_config_summary),
                largeIcon = true,
            )
        }
        item { WearSettingsJumpPageWidget(spec, stringResource(R.string.search_apps), { onOpen(WearRoute.DynamicManagerApps) }, icon = Icons.Rounded.Apps, largeIcon = true) }
        item { WearActionButton(spec, Icons.Rounded.Delete, stringResource(R.string.dynamic_manager_clear_config), clear::show, largeIcon = true) }
    }
}

@Composable
internal fun WearDynamicManagerManualPage(
    state: DynamicManagerUiState,
    size: String,
    hash: String,
    onBack: () -> Unit,
    onOpen: (WearRoute) -> Unit,
    onSave: (Int, String) -> Unit,
) {
    var result by remember { mutableStateOf<WearResult?>(null) }
    WearResultDialog(result) { result = null }
    val failed = stringResource(R.string.operation_failed)
    val invalidHash = stringResource(R.string.hash_must_be_64_chars)
    val grant = rememberWearConfirmDialog(
        stringResource(R.string.dynamic_manager_grant_confirm_title),
        stringResource(R.string.dynamic_manager_grant_confirm_message),
    ) {
        size.toIntOrNull()?.let { onSave(it, hash.trim().lowercase(Locale.ROOT)) }
    }
    WearList(
        isLoading = state.isLoading || state.isSubmitting,
        onBack = onBack,
        onConfirm = {
            val length = size.toIntOrNull()
            val signatureHash = hash.trim().lowercase(Locale.ROOT)
            when {
                length == null || length !in 0x100..0x1000 -> result = WearResult(failed, success = false)
                !signatureHash.matches(Regex("[0-9a-f]{64}")) -> result = WearResult(invalidHash, success = false)
                else -> grant.show()
            }
        },
        confirmLabel = stringResource(R.string.wear_save_configuration),
        confirmEnabled = !state.isSubmitting,
    ) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.dynamic_manager_manual_config)) }
        item { WearSectionHeader(spec, stringResource(R.string.wear_signature_information)) }
        item {
            WearSettingsJumpPageWidget(
                spec,
                stringResource(R.string.signature_size),
                { onOpen(WearRoute.DynamicManagerSize) },
                icon = Icons.Rounded.FormatSize,
                largeIcon = true,
                description = size.ifBlank { null },
            )
        }
        item {
            WearSettingsJumpPageWidget(
                spec,
                stringResource(R.string.signature_hash),
                { onOpen(WearRoute.DynamicManagerHash) },
                icon = Icons.Rounded.Fingerprint,
                largeIcon = true,
                description = hash.ifBlank { null },
            )
        }
    }
}

@Composable
internal fun WearDynamicManagerAppsPage(
    state: DynamicManagerUiState,
    onBack: () -> Unit,
    onOpenPanel: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (DynamicManagerApp) -> Unit,
) {
    var pending by remember { mutableStateOf<DynamicManagerApp?>(null) }
    val grant = rememberWearConfirmDialog(
        stringResource(R.string.dynamic_manager_grant_confirm_title),
        stringResource(R.string.dynamic_manager_grant_confirm_message),
    ) {
        pending?.let(onSelect)
        pending = null
    }
    val needsRetry = state.loadFailed || state.apps.isEmpty()
    WearList(
        isLoading = state.isLoading || state.isSubmitting,
        isRefreshing = state.isRefreshing && !state.isLoading,
        onOpenPanel = onOpenPanel,
        hasOperationsPanel = true,
        panelLabel = stringResource(R.string.wear_operations),
        onBack = onBack,
        onConfirm = onRetry.takeIf { needsRetry },
        confirmLabel = stringResource(R.string.network_retry),
        confirmEnabled = !state.isRefreshing,
    ) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.search_apps)) }
        if (needsRetry) {
            item {
                WearEmptyState(
                    spec,
                    if (state.loadFailed) {
                        Icons.Rounded.Warning
                    } else if (state.search.isNotBlank()) {
                        Icons.Rounded.SearchOff
                    } else {
                        Icons.Rounded.Apps
                    },
                    stringResource(
                        if (state.loadFailed) {
                            R.string.wear_load_failed
                        } else if (state.search.isNotBlank()) {
                            R.string.search_no_any_match
                        } else {
                            R.string.no_apps_found
                        },
                    ),
                )
            }
        }
        LazySegmentedColumn(if (state.loadFailed) emptyList() else state.apps, { it.packageName }) { app ->
            val source = app.managerSourceLabel()?.let { stringResource(it) }
            WearSettingsSwitchWidget(
                spec,
                label = app.label,
                checked = app.isSelected || !app.isChangeable,
                onCheckedChange = { checked ->
                    if (checked) {
                        pending = app
                        grant.show()
                    }
                },
                secondaryLabel = source?.let { stringResource(R.string.dynamic_manager_fixed_manager_summary, app.packageName, it) } ?: app.packageName,
                secondaryLabelMaxLines = Int.MAX_VALUE,
                enabled = app.isChangeable,
                iconContent = {
                    Box(Modifier.size(ButtonDefaults.LargeIconSize)) {
                        Icon(Icons.Rounded.Apps, null, Modifier.fillMaxSize())
                        PackageIcon(app.packageName, null, Modifier.fillMaxSize())
                    }
                },
                largeIcon = true,
            )
        }
    }
}

/** A text field of the umount path page, edited on its own input page. */
private enum class UmountInput { Path, Flags }

@Composable
internal fun WearUmountPage(viewModel: UmountManagerScreenViewModel, onBack: () -> Unit, onOpenPanel: () -> Unit, query: String = "") {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var result by remember { mutableStateOf<WearResult?>(null) }
    val active by rememberUpdatedState(LocalScreenIsActive.current)
    WearResultDialog(result) { result = null }
    LaunchedEffect(viewModel) {
        viewModel.events.collect {
            if (it is UmountManagerUiEvent.Message && !it.added && active) result = WearResult(it.message, success = it.success)
        }
    }
    WearUmountPage(state, onBack, onOpenPanel, query) { viewModel.dispatch(UmountManagerUiAction.Remove(it)) }
}

@Composable
internal fun WearUmountPage(state: UmountManagerUiState, onBack: () -> Unit, onOpenPanel: () -> Unit, query: String = "", onRemove: (UmountPath) -> Unit) {
    var selected by remember { mutableStateOf<UmountPath?>(null) }
    val remove = rememberWearConfirmDialog(stringResource(R.string.delete), stringResource(R.string.confirm_delete), destructive = true) {
        selected?.let(onRemove)
        selected = null
    }
    val paths = state.umountPaths.filter { it.path.contains(query) }
    val listState = rememberTransformingLazyColumnState()
    WearList(
        isLoading = state.isLoading,
        isRefreshing = state.isRefreshing,
        onBack = onBack,
        onOpenPanel = onOpenPanel,
        hasOperationsPanel = true,
        panelLabel = stringResource(R.string.wear_operations),
        listState = listState,
    ) { spec ->
        item { WearPageHeader(spec, stringResource(if (query.isEmpty()) R.string.umount_path_manager else R.string.wear_search_umount_paths)) }
        if (paths.isEmpty()) {
            item { WearEmptyState(spec, Icons.Rounded.Folder, stringResource(if (state.umountPaths.isEmpty()) R.string.no_any_umount_path else R.string.search_no_any_match)) }
        }
        items(paths, key = { it.path }) { entry ->
            WearModuleSwipeActions(
                spec,
                scrolling = listState.isScrollInProgress,
                primary = WearIconAction(Icons.Rounded.Delete, stringResource(R.string.delete), {
                    selected = entry
                    remove.show()
                }, destructive = true),
            ) {
                WearActionButton(null, entry.path, {}, secondaryText = entry.flags.toUmountFlagName())
            }
        }
    }
}

@Composable
internal fun WearAddUmountPage(onBack: () -> Unit, viewModel: UmountManagerScreenViewModel = koinViewModel()) {
    var result by remember { mutableStateOf<WearResult?>(null) }
    WearResultDialog(result) { result = null }
    val failed = stringResource(R.string.operation_failed)
    var submitting by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf<UmountInput?>(null) }
    var path by rememberSaveable { mutableStateOf("") }
    var flags by rememberSaveable { mutableStateOf("0") }
    LaunchedEffect(viewModel) {
        viewModel.events.collect {
            if (it is UmountManagerUiEvent.Message && it.added && submitting) {
                submitting = false
                if (it.success) onBack() else result = WearResult(it.message, success = false)
            }
        }
    }
    val editing = input
    if (editing != null) {
        WearSubPage({ input = null }) {
            WearTextInputPage(
                stringResource(if (editing == UmountInput.Path) R.string.add_umount_path else R.string.umount_flags),
                if (editing == UmountInput.Path) path else flags,
            ) {
                if (editing == UmountInput.Path) path = it else flags = it
                input = null
            }
        }
        return
    }
    WearList(onBack = onBack, isBusy = submitting, confirmLabel = stringResource(R.string.add), confirmEnabled = !submitting, onConfirm = {
        val parsed = flags.toIntOrNull()
        if (!path.startsWith('/') || parsed == null) {
            result = WearResult(failed, success = false)
        } else {
            submitting = true
            viewModel.dispatch(UmountManagerUiAction.Add(path, parsed))
        }
    }) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.add_umount_path)) }
        SegmentedColumn(UmountInput.entries, { it }) { field ->
            WearSettingsJumpPageWidget(
                spec,
                stringResource(if (field == UmountInput.Path) R.string.add_umount_path else R.string.umount_flags),
                { input = field },
                icon = if (field == UmountInput.Path) Icons.Rounded.Folder else Icons.Rounded.Flag,
                description = if (field == UmountInput.Path) path.ifEmpty { null } else flags,
                enabled = !submitting,
            )
        }
        item {
            Text(
                stringResource(R.string.umount_flags_hint),
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Permanent uninstall or restoring the stock image; [onStart] receives whether it restores. */
@Composable
internal fun WearUninstallPage(onBack: () -> Unit, onStart: (restore: Boolean) -> Unit) {
    var selected by remember { mutableStateOf(UninstallType.PERMANENT) }
    val confirm = rememberWearConfirmDialog(stringResource(selected.title), stringResource(selected.message), destructive = true) {
        onStart(selected == UninstallType.RESTORE_STOCK_IMAGE)
    }
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.settings_uninstall)) }
        listOf(UninstallType.PERMANENT, UninstallType.RESTORE_STOCK_IMAGE).forEach { option ->
            item {
                val icon = if (option == UninstallType.RESTORE_STOCK_IMAGE) Icons.Rounded.Undo else Icons.Rounded.DeleteForever
                WearActionButton(spec, icon, stringResource(option.title), {
                    selected = option
                    confirm.show()
                })
            }
        }
    }
}
