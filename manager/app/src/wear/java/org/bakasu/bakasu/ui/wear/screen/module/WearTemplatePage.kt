package org.bakasu.bakasu.ui.wear.screen.module

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.bakasu.bakasu.ui.viewmodel.TemplateEditorUiAction
import org.bakasu.bakasu.ui.viewmodel.TemplateEditorUiEvent
import org.bakasu.bakasu.ui.viewmodel.TemplateEditorViewModel
import org.bakasu.bakasu.ui.viewmodel.TemplateUiAction
import org.bakasu.bakasu.ui.viewmodel.TemplateUiEvent
import org.bakasu.bakasu.ui.viewmodel.TemplateViewModel
import org.bakasu.bakasu.ui.wear.WearNavigator
import org.bakasu.bakasu.ui.wear.WearRoute
import org.bakasu.bakasu.ui.wear.component.LocalWearToast
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone
import org.bakasu.bakasu.ui.wear.component.WearSubNavigation
import org.bakasu.bakasu.ui.wear.component.WearSwipeToDelete
import org.bakasu.bakasu.ui.wear.component.WearTextInputPage
import org.bakasu.bakasu.ui.wear.component.rememberWearConfirmDialog
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.wearProfileFieldIcon
import org.bakasu.bakasu.ui.wear.rememberWearNavigator
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearTemplatePage(
    state: org.bakasu.bakasu.ui.viewmodel.TemplateUiState,
    onBack: () -> Unit,
    onOpenPanel: () -> Unit,
    onOpenTemplate: (id: String, readOnly: Boolean, creation: Boolean) -> Unit,
    onDelete: (String) -> Unit,
    searchResults: Boolean = false,
) {
    val listState = rememberTransformingLazyColumnState()
    val shownTemplates = if (searchResults) state.searchResults else state.templateList
    // Local templates are deleted only after confirmation; cloud templates stay read-only.
    var pendingDelete by remember { mutableStateOf<ProfileTemplate?>(null) }
    val deleteDialog = rememberWearConfirmDialog(
        stringResource(R.string.app_profile_template_delete),
        pendingDelete?.let { stringResource(R.string.wear_template_delete_message, it.name.ifBlank { it.id }) }.orEmpty(),
        destructive = true,
    ) {
        pendingDelete?.let { onDelete(it.id) }
        pendingDelete = null
    }
    WearList(
        pageTitle = stringResource(if (searchResults) R.string.wear_template_search else R.string.settings_profile_template),
        isLoading = state.isLoading,
        isRefreshing = state.isRefreshing && !state.isLoading,
        onBack = onBack,
        listState = listState,
        onOpenPanel = onOpenPanel,
        hasOperationsPanel = true,
        panelLabel = stringResource(R.string.wear_operations),
    ) { spec ->
        if (state.loadFailed) {
            item { WearStatusItem(spec, Icons.Rounded.Error, stringResource(R.string.wear_load_failed), tone = WearStatusTone.ERROR) }
        } else if (!state.isLoading && shownTemplates.isEmpty()) {
            item { WearEmptyState(spec, Icons.Rounded.Description, stringResource(if (searchResults) R.string.wear_template_no_matches else R.string.wear_template_empty)) }
        }
        // Items are keyed by the template ID, so a reveal stays with its template as the list changes.
        LazySegmentedColumn(shownTemplates, { it.id }) { template ->
            val row: @Composable () -> Unit = {
                WearSettingsJumpPageWidget(
                    spec,
                    template.name.ifBlank { template.id },
                    { onOpenTemplate(template.id, !template.local, false) },
                    icon = Icons.Rounded.Description,
                    description = template.description.ifBlank { null },
                    transformed = !template.local,
                )
            }
            if (!template.local) {
                row()
                return@LazySegmentedColumn
            }
            WearSwipeToDelete(
                spec,
                listState.isScrollInProgress,
                stringResource(R.string.app_profile_template_delete),
                onDelete = {
                    pendingDelete = template
                    deleteDialog.show()
                },
                content = row,
            )
        }
    }
}

/** One collector handles clipboard results for both the management list and its menu. */
@Composable
internal fun rememberWearTemplateImport(viewModel: TemplateViewModel): () -> Unit {
    val toast = LocalWearToast.current
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val imported = stringResource(R.string.app_profile_template_import_success)
    val emptyExport = stringResource(R.string.app_profile_template_export_empty)
    val emptyClipboard = stringResource(R.string.app_profile_template_import_empty)
    val exported = stringResource(R.string.app_profile_export_to_clipboard)
    val failed = stringResource(R.string.operation_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TemplateUiEvent.ImportCompleted -> {
                    toast.show(imported)
                }

                is TemplateUiEvent.Exported -> {
                    clipboard.setPrimaryClip(ClipData.newPlainText(exported, event.json))
                    toast.show(exported)
                }

                TemplateUiEvent.ExportEmpty -> {
                    toast.show(emptyExport)
                }

                is TemplateUiEvent.Error -> {
                    val message = event.message.ifBlank { failed }
                    toast.show(message)
                }
            }
        }
    }
    return {
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (text.isNullOrBlank()) toast.show(emptyClipboard) else viewModel.dispatch(TemplateUiAction.Import(text))
    }
}

@Composable
internal fun WearTemplateEditor(id: String, readOnly: Boolean, creation: Boolean, registerBack: ((() -> Unit)?) -> Unit = {}, onBack: () -> Unit) {
    val viewModel = koinViewModel<TemplateEditorViewModel>(parameters = { parametersOf(id, readOnly, creation) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast = LocalWearToast.current
    val created = stringResource(R.string.wear_template_created)
    val failed = stringResource(R.string.app_profile_template_save_failed)
    LaunchedEffect(viewModel, toast, created, failed) {
        viewModel.events.collect { event ->
            when (event) {
                TemplateEditorUiEvent.Deleted -> onBack()

                TemplateEditorUiEvent.Saved -> {
                    if (creation) toast.show(created)
                    onBack()
                }

                is TemplateEditorUiEvent.Error -> toast.show(failed)
            }
        }
    }
    val template = state.template
    val fields = TEMPLATE_FIELDS
    val navigator = rememberWearNavigator(onExit = onBack, initialRoute = WearRoute.TemplateEditor(id, readOnly, creation))
    val leave by rememberUpdatedState<() -> Unit> {
        if (navigator.backStack.size > 1) {
            navigator.back()
        } else {
            onBack()
        }
    }
    DisposableEffect(viewModel) {
        registerBack { leave() }
        onDispose { registerBack(null) }
    }
    WearSubNavigation(navigator) { route ->
        val shownInput = (route as? WearRoute.TemplateField)?.field
        if (shownInput != null) {
            if (readOnly || shownInput == R.string.app_profile_template_id && !creation) {
                WearList(onBack = navigator::back) { spec ->
                    item { WearPageHeader(spec, stringResource(shownInput)) }
                    template.field(shownInput).chunked(350).forEach { chunk ->
                        item { WearInfoCard(spec) { Text(chunk) } }
                    }
                }
            } else {
                WearTextInputPage(stringResource(shownInput), template.field(shownInput), multiline = shownInput == R.string.profile_selinux_rules) { text ->
                    runCatching { template.withField(shownInput, text) }.fold(onSuccess = {
                        viewModel.dispatch(TemplateEditorUiAction.Update(it))
                        navigator.back()
                    }, onFailure = { toast.show(failed) })
                }
            }
        } else {
            WearList(
                isLoading = state.loading,
                onBack = { leave() },
                confirmEnabled = !state.saving && (creation || state.dirty),
                onConfirm = if (!readOnly && state.loadFailure == null) ({ viewModel.dispatch(TemplateEditorUiAction.Save) }) else null,
                confirmLabel = stringResource(R.string.app_profile_template_save),
                confirmIcon = Icons.Rounded.Save,
            ) { spec ->
                item { WearPageHeader(spec, stringResource(if (readOnly) R.string.app_profile_template_view else R.string.app_profile_template_edit)) }
                if (state.loadFailure != null) item { WearStatusItem(spec, Icons.Rounded.Error, failed, tone = WearStatusTone.ERROR) }
                if (state.loadFailure == null) {
                    // A TransformingLazyColumn item places only its last child, so the value is the widget's description.
                    SegmentedColumn(fields, { it }) { field ->
                        WearSettingsJumpPageWidget(
                            spec,
                            stringResource(field),
                            { navigator.open(WearRoute.TemplateField(field)) },
                            icon = wearProfileFieldIcon(field),
                            description = template.field(field).ifEmpty { null },
                        )
                    }
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

/** Re-searching from the results menu updates its existing destination. */
internal fun WearNavigator.submitTemplateSearch(query: String, search: (String) -> Unit) {
    if (query.isNotBlank() && backStack.dropLast(2).lastOrNull() == WearRoute.TemplateSearchResults) {
        back()
    }
    submitSearch(WearRoute.TemplateSearchResults, query, search)
}
