package org.bakasu.bakasu.ui.wear

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.bakasu.bakasu.domain.model.ProfileTemplateFailure
import org.bakasu.bakasu.domain.usecase.IsNetworkAvailableUseCase
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.viewmodel.OnlineTemplateUiAction
import org.bakasu.bakasu.ui.viewmodel.OnlineTemplateUiEvent
import org.bakasu.bakasu.ui.viewmodel.OnlineTemplateViewModel
import org.bakasu.bakasu.ui.viewmodel.TemplateEditorUiAction
import org.bakasu.bakasu.ui.viewmodel.TemplateEditorUiEvent
import org.bakasu.bakasu.ui.viewmodel.TemplateEditorViewModel
import org.bakasu.bakasu.ui.viewmodel.TemplateUiAction
import org.bakasu.bakasu.ui.viewmodel.TemplateUiEvent
import org.bakasu.bakasu.ui.viewmodel.TemplateViewModel
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearDetailField
import org.bakasu.bakasu.ui.wear.component.WearDialogIcon
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearResult
import org.bakasu.bakasu.ui.wear.component.WearResultDialog
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone
import org.bakasu.bakasu.ui.wear.component.WearSubPage
import org.bakasu.bakasu.ui.wear.component.WearSwipeToDelete
import org.bakasu.bakasu.ui.wear.component.WearTextInputPage
import org.bakasu.bakasu.ui.wear.component.rememberWearConfirmDialog
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** The phone template list: local and synchronized templates, import, export and synchronization. */
@Composable
internal fun WearTemplatePage(
    onBack: () -> Unit,
    onOpenOnline: () -> Unit,
    onOpenTemplate: (id: String, readOnly: Boolean, creation: Boolean) -> Unit,
) {
    val viewModel = koinViewModel<TemplateViewModel>()
    val isNetworkAvailable = koinInject<IsNetworkAvailableUseCase>()
    // Without a network the online templates are not requested; the dialog says why.
    val offlineDialog = rememberWearOfflineDialog()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val listState = rememberTransformingLazyColumnState()
    // A local template is deleted only after this confirmation; the swipe only asks for it.
    var pendingDelete by remember { mutableStateOf<ProfileTemplate?>(null) }
    val deleteDialog = rememberWearConfirmDialog(
        stringResource(R.string.app_profile_template_delete),
        pendingDelete?.let { stringResource(R.string.wear_template_delete_message, it.name.ifBlank { it.id }) }.orEmpty(),
        destructive = true,
    ) {
        pendingDelete?.let { viewModel.dispatch(TemplateUiAction.Delete(it.id)) }
        pendingDelete = null
    }
    var result by remember { mutableStateOf<WearResult?>(null) }
    WearResultDialog(result) { result = null }
    val imported = stringResource(R.string.app_profile_template_import_success)
    val emptyExport = stringResource(R.string.app_profile_template_export_empty)
    val emptyClipboard = stringResource(R.string.app_profile_template_import_empty)
    val exported = stringResource(R.string.app_profile_export_to_clipboard)
    val failed = stringResource(R.string.operation_failed)
    LaunchedEffect(viewModel) {
        viewModel.dispatch(TemplateUiAction.Refresh())
        viewModel.events.collect { event ->
            when (event) {
                TemplateUiEvent.ImportCompleted -> {
                    result = WearResult(imported, success = true)
                    viewModel.dispatch(TemplateUiAction.Refresh())
                }

                is TemplateUiEvent.Exported -> {
                    clipboard.setPrimaryClip(ClipData.newPlainText(exported, event.json))
                    result = WearResult(exported, success = true)
                }

                TemplateUiEvent.ExportEmpty -> result = WearResult(emptyExport, success = false)

                is TemplateUiEvent.Error -> result = WearResult(event.message.ifBlank { failed }, success = false)
            }
        }
    }
    WearList(isLoading = state.isRefreshing && state.templateList.isEmpty(), onBack = onBack, listState = listState) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.settings_profile_template)) }
        item {
            WearActionButton(
                spec,
                Icons.Rounded.Add,
                stringResource(R.string.app_profile_template_create),
                { onOpenTemplate("", false, true) },
            )
        }
        item {
            WearActionButton(spec, Icons.Rounded.Cloud, stringResource(R.string.wear_template_view_online), {
                if (isNetworkAvailable()) onOpenOnline() else offlineDialog.show()
            })
        }
        item {
            WearActionButton(spec, Icons.Rounded.ContentPaste, stringResource(R.string.app_profile_import_from_clipboard), {
                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (text.isNullOrBlank()) result = WearResult(emptyClipboard, success = false) else viewModel.dispatch(TemplateUiAction.Import(text))
            })
        }
        item { WearActionButton(spec, Icons.Rounded.ContentCopy, exported, { viewModel.dispatch(TemplateUiAction.Export) }) }
        // Items are keyed by the template ID, so a reveal stays with its template as the list changes.
        LazySegmentedColumn(state.templateList, { it.id }) { template ->
            val open = { onOpenTemplate(template.id, !template.local, false) }
            if (template.local) {
                // Local templates, which App Profile can select, reveal Delete when swiped right to left.
                WearSwipeToDelete(
                    spec,
                    listState.isScrollInProgress,
                    stringResource(R.string.app_profile_template_delete),
                    onDelete = {
                        pendingDelete = template
                        deleteDialog.show()
                    },
                ) {
                    WearSettingsJumpPageWidget(
                        spec,
                        template.name.ifBlank { template.id },
                        open,
                        icon = Icons.Rounded.Description,
                        description = template.description.ifBlank { null },
                        transformed = false,
                    )
                }
            } else {
                WearSettingsJumpPageWidget(
                    spec,
                    template.name.ifBlank { template.id },
                    open,
                    icon = Icons.Rounded.Description,
                    description = template.description.ifBlank { null },
                )
            }
        }
    }
}

/** The no-network AlertDialog; it only closes, as nothing can be loaded without a network. */
@Composable
private fun rememberWearOfflineDialog(onDismissed: () -> Unit = {}) = rememberCustomDialog { dismiss ->
    val close = {
        dismiss()
        onDismissed()
    }
    AlertDialog(
        visible = true,
        onDismissRequest = close,
        icon = { WearDialogIcon(Icons.Rounded.ErrorOutline) },
        title = { Text(stringResource(R.string.wear_template_offline)) },
        text = { Text(stringResource(R.string.wear_no_internet)) },
        edgeButton = { AlertDialogDefaults.EdgeButton(onClick = close) },
    )
}

/**
 * Online templates for browsing. Loading or viewing them stores nothing; only Add stores the chosen
 * template as a local one, which App Profile can then select.
 */
@Composable
internal fun WearOnlineTemplatePage(onBack: () -> Unit) {
    val viewModel = koinViewModel<OnlineTemplateViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<WearResult?>(null) }
    var failure by remember { mutableStateOf<ProfileTemplateFailure?>(null) }
    WearResultDialog(result) { result = null }
    val added = stringResource(R.string.wear_template_added)
    val alreadyAdded = stringResource(R.string.wear_template_already_added)
    val failed = stringResource(R.string.operation_failed)
    val offlineDialog = rememberWearOfflineDialog(onBack)
    // A request or parsing failure leaves nothing to browse, so it asks to retry or leave.
    val failureDialog = rememberCustomDialog { dismiss ->
        val leave = {
            dismiss()
            onBack()
        }
        AlertDialog(
            visible = true,
            onDismissRequest = leave,
            icon = { WearDialogIcon(Icons.Rounded.Info) },
            title = { Text(stringResource(R.string.wear_repo_connect_failed)) },
            text = {
                Text(
                    stringResource(
                        if (failure == ProfileTemplateFailure.Invalid) R.string.wear_template_online_invalid else R.string.wear_template_online_failed,
                    ),
                )
            },
            confirmButton = {
                AlertDialogDefaults.ConfirmButton(
                    onClick = {
                        dismiss()
                        viewModel.dispatch(OnlineTemplateUiAction.Load)
                    },
                )
            },
            dismissButton = { AlertDialogDefaults.DismissButton(onClick = leave) },
        )
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OnlineTemplateUiEvent.LoadFailed -> {
                    failure = event.reason
                    if (event.reason == ProfileTemplateFailure.Offline) offlineDialog.show() else failureDialog.show()
                }

                OnlineTemplateUiEvent.Added -> result = WearResult(added, success = true)

                is OnlineTemplateUiEvent.AddFailed -> result = WearResult(
                    if (event.reason == ProfileTemplateFailure.Conflict) alreadyAdded else failed,
                    success = false,
                )
            }
        }
    }
    val selected = state.templates.firstOrNull { it.id == selectedId }
    if (selected != null) {
        WearSubPage({ selectedId = null }) {
            WearList(onBack = { selectedId = null }) { spec ->
                item { WearPageHeader(spec, selected.name.ifBlank { selected.id }) }
                TEMPLATE_FIELDS.filter { selected.field(it).isNotEmpty() }.forEach { field ->
                    item { WearInfoCard(spec) { WearDetailField(templateFieldIcon(field), stringResource(field), selected.field(field)) } }
                }
                item {
                    val isAdded = selected.id in state.addedIds
                    WearActionButton(
                        spec,
                        if (isAdded) Icons.Rounded.CheckCircle else Icons.Rounded.Add,
                        stringResource(if (isAdded) R.string.wear_template_added else R.string.wear_template_add),
                        { viewModel.dispatch(OnlineTemplateUiAction.Add(selected)) },
                        enabled = !isAdded && !state.adding,
                    )
                }
            }
        }
        return
    }
    WearList(isLoading = state.loading, onBack = onBack, onRefresh = { viewModel.dispatch(OnlineTemplateUiAction.Load) }) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_template_online)) }
        if (state.templates.isEmpty()) {
            item { WearStatusItem(spec, Icons.Rounded.Cloud, stringResource(R.string.wear_template_online_empty)) }
        }
        LazySegmentedColumn(state.templates, { it.id }) { template ->
            WearSettingsJumpPageWidget(
                spec,
                template.name.ifBlank { template.id },
                { selectedId = template.id },
                icon = if (template.id in state.addedIds) Icons.Rounded.CheckCircle else Icons.Rounded.Description,
                description = template.description.ifBlank { null },
            )
        }
    }
}

@Composable
internal fun WearTemplateEditor(id: String, readOnly: Boolean, creation: Boolean, onBack: () -> Unit) {
    val viewModel = koinViewModel<TemplateEditorViewModel>(parameters = { parametersOf(id, readOnly, creation) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failed = stringResource(R.string.app_profile_template_save_failed)
    var result by remember { mutableStateOf<WearResult?>(null) }
    WearResultDialog(result) { result = null }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TemplateEditorUiEvent.Deleted, TemplateEditorUiEvent.Saved -> onBack()
                is TemplateEditorUiEvent.Error -> result = WearResult(failed, success = false)
            }
        }
    }
    val template = state.template
    val fields = TEMPLATE_FIELDS
    var input by rememberSaveable { mutableIntStateOf(0) }
    val delete = rememberWearConfirmDialog(stringResource(R.string.app_profile_template_delete), stringResource(R.string.confirm_delete), destructive = true) {
        viewModel.dispatch(TemplateEditorUiAction.Delete)
    }
    val shownInput = input
    if (shownInput != 0) {
        WearSubPage({ input = 0 }) {
            if (readOnly || shownInput == R.string.app_profile_template_id && !creation) {
                WearList(onBack = { input = 0 }) { spec ->
                    item { WearPageHeader(spec, stringResource(shownInput)) }
                    template.field(shownInput).chunked(350).forEach { chunk ->
                        item { WearInfoCard(spec) { Text(chunk) } }
                    }
                }
            } else {
                WearTextInputPage(stringResource(shownInput), template.field(shownInput), multiline = shownInput == R.string.profile_selinux_rules) { text ->
                    runCatching { template.withField(shownInput, text) }.fold(onSuccess = { viewModel.dispatch(TemplateEditorUiAction.Update(it)) }, onFailure = { result = WearResult(failed, success = false) })
                    input = 0
                }
            }
        }
    } else {
        WearList(isLoading = state.loading, onBack = onBack) { spec ->
            item { WearPageHeader(spec, stringResource(if (readOnly) R.string.app_profile_template_view else R.string.app_profile_template_edit)) }
            if (state.loadFailure != null) item { WearStatusItem(spec, Icons.Rounded.Error, failed, tone = WearStatusTone.ERROR) }
            if (state.loadFailure == null) {
                // A TransformingLazyColumn item places only its last child, so the value is the widget's description.
                SegmentedColumn(fields, { it }) { field ->
                    WearSettingsJumpPageWidget(
                        spec,
                        stringResource(field),
                        { input = field },
                        icon = templateFieldIcon(field),
                        description = template.field(field).ifEmpty { null },
                    )
                }
                if (!readOnly) {
                    item { WearActionButton(spec, Icons.Rounded.Save, stringResource(R.string.app_profile_template_save), { viewModel.dispatch(TemplateEditorUiAction.Save) }) }
                    if (!creation) item { WearActionButton(spec, Icons.Rounded.Delete, stringResource(R.string.app_profile_template_delete), delete::show) }
                }
            }
        }
    }
}

private val TEMPLATE_FIELDS = listOf(
    R.string.app_profile_template_id, R.string.app_profile_template_name,
    R.string.app_profile_template_description, R.string.module_author, R.string.wear_uid, R.string.wear_gid,
    R.string.profile_namespace, R.string.profile_groups, R.string.profile_capabilities,
    R.string.profile_selinux_context, R.string.profile_selinux_rules, R.string.profile_flags,
)

private fun templateFieldIcon(field: Int) = when (field) {
    R.string.app_profile_template_id, R.string.wear_uid, R.string.wear_gid -> Icons.Rounded.Numbers
    R.string.app_profile_template_name -> Icons.Rounded.Edit
    R.string.app_profile_template_description -> Icons.Rounded.Description
    R.string.module_author -> Icons.Rounded.Person
    R.string.profile_namespace -> Icons.Rounded.Storage
    R.string.profile_groups -> Icons.Rounded.Group
    R.string.profile_flags -> Icons.Rounded.Flag
    else -> Icons.Rounded.Security
}

private fun ProfileTemplate.field(field: Int): String = when (field) {
    R.string.app_profile_template_id -> id
    R.string.app_profile_template_name -> name
    R.string.app_profile_template_description -> description
    R.string.module_author -> author
    R.string.wear_uid -> uid.toString()
    R.string.wear_gid -> gid.toString()
    R.string.profile_namespace -> namespace.toString()
    R.string.profile_groups -> groups.joinToString(", ")
    R.string.profile_capabilities -> capabilities.joinToString(", ")
    R.string.profile_selinux_context -> context
    R.string.profile_selinux_rules -> rules.joinToString("\n")
    else -> flags.joinToString(", ")
}

private fun ProfileTemplate.withField(field: Int, value: String): ProfileTemplate {
    fun numbers(): List<Int> = value.split(Regex("[,\\s]+")).filter { it.isNotBlank() }.map(String::toInt)
    return when (field) {
        R.string.app_profile_template_id -> copy(id = value.trim())
        R.string.app_profile_template_name -> copy(name = value)
        R.string.app_profile_template_description -> copy(description = value)
        R.string.module_author -> copy(author = value)
        R.string.wear_uid -> copy(uid = value.toInt().also { require(it >= 0) })
        R.string.wear_gid -> copy(gid = value.toInt().also { require(it >= 0) })
        R.string.profile_namespace -> copy(namespace = value.toInt().also { require(it in 0..2) })
        R.string.profile_groups -> copy(groups = numbers().onEach { require(it >= 0) })
        R.string.profile_capabilities -> copy(capabilities = numbers().onEach { require(it in 0..40) })
        R.string.profile_selinux_context -> copy(context = value)
        R.string.profile_selinux_rules -> copy(rules = value.lines().filter { it.isNotBlank() })
        else -> copy(flags = numbers().onEach { require(it in 0 until org.bakasu.bakasu.Natives.Profile.RootProfileFlag.entries.size) })
    }
}
