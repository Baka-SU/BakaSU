package com.resukisu.resukisu.ui.wear

import androidx.compose.material.icons.twotone.Error
import com.resukisu.resukisu.ui.component.wear.WearStatusTone
import com.resukisu.resukisu.ui.component.wear.WearStatusItem
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.ContentPaste
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.Flag
import androidx.compose.material.icons.twotone.Group
import androidx.compose.material.icons.twotone.Numbers
import androidx.compose.material.icons.twotone.Person
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Storage
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.ProfileTemplate
import com.resukisu.resukisu.ui.component.settings.WearSettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.LazySegmentedColumn
import com.resukisu.resukisu.ui.component.wear.*
import com.resukisu.resukisu.ui.viewmodel.*
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.flow.map

@Composable
internal fun WearTemplatePage(onBack: () -> Unit) {
    val viewModel = koinViewModel<TemplateViewModel>(key = "wear-templates",
        parameters = { parametersOf(true) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var message by remember { mutableStateOf<String?>(null) }
    val imported = stringResource(R.string.app_profile_template_import_success)
    val emptyExport = stringResource(R.string.app_profile_template_export_empty)
    val emptyClipboard = stringResource(R.string.app_profile_template_import_empty)
    val exported = stringResource(R.string.app_profile_export_to_clipboard)
    val failed = stringResource(R.string.operation_failed)
    LaunchedEffect(viewModel) { viewModel.events.collect { event -> when (event) {
        TemplateUiEvent.ImportCompleted -> { message = imported; viewModel.dispatch(TemplateUiAction.Refresh()) }
        is TemplateUiEvent.Exported -> { clipboard.setPrimaryClip(ClipData.newPlainText(exported, event.json)); message = exported }
        TemplateUiEvent.ExportEmpty -> message = emptyExport
        is TemplateUiEvent.Error -> message = event.message.ifBlank { failed }
    } } }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var readOnly by rememberSaveable { mutableStateOf(true) }
    var creation by rememberSaveable { mutableStateOf(false) }
    var editRequest by rememberSaveable { mutableIntStateOf(0) }
    var online by rememberSaveable { mutableStateOf(false) }
    var onlineSelection by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberTransformingLazyColumnState()
    val route = if (online) onlineSelection?.let { "online:$it" } ?: "online"
        else selected?.let { "template:$it" }.orEmpty()
    WearPageTransition(route, if (route.startsWith("online:")) 2 else if (route.isEmpty()) 0 else 1) { shownPage ->
        if (shownPage == "online" || shownPage.startsWith("online:")) {
            val template = state.onlineTemplates.firstOrNull { "online:${it.id}" == shownPage }
            val back = { if (onlineSelection != null) onlineSelection = null else online = false }
            WearSubPage(back) {
                WearList(isLoading = state.isLoadingOnline && state.onlineTemplates.isEmpty(), onBack = back) { spec ->
                    item { WearPageHeader(spec, null, stringResource(R.string.wear_online_templates)) }
                    message?.let { item { WearInfoCard(spec) { Text(it) } } }
                    if (template == null) {
                        item { WearActionButton(spec, Icons.TwoTone.Refresh, stringResource(R.string.wear_refresh),
                            { message = null; viewModel.dispatch(TemplateUiAction.BrowseOnline) }, !state.isLoadingOnline) }
                        LazySegmentedColumn(state.onlineTemplates, { it.id }) { entry ->
                            WearSettingsJumpPageWidget(spec, entry.name.ifBlank { entry.id },
                                { onlineSelection = entry.id; message = null }, icon = Icons.TwoTone.Description,
                                description = entry.description.ifBlank { null })
                        }
                    } else {
                        val fields = listOf(R.string.app_profile_template_id, R.string.app_profile_template_name,
                            R.string.app_profile_template_description, R.string.module_author, R.string.wear_uid,
                            R.string.wear_gid, R.string.profile_namespace, R.string.profile_groups,
                            R.string.profile_capabilities, R.string.profile_selinux_context,
                            R.string.profile_selinux_rules, R.string.profile_flags)
                        fields.forEach { field -> item {
                            WearInfoCard(spec) { WearDetailField(templateFieldIcon(field), stringResource(field), template.field(field)) }
                        } }
                        item { WearActionButton(spec, Icons.TwoTone.Add, stringResource(R.string.add),
                            { viewModel.dispatch(TemplateUiAction.AddOnline(template)) },
                            enabled = state.templateList.none { it.id == template.id && it.local }) }
                    }
                }
            }
        } else if (shownPage.isNotEmpty()) {
            val id = shownPage.removePrefix("template:")
            WearSubPage({ selected = null }) {
                WearTemplateEditor(id, readOnly, creation, editRequest, { selected = null }) {
                    selected = null; viewModel.dispatch(TemplateUiAction.Refresh())
                }
            }
        } else WearList(isLoading = state.isRefreshing && state.templateList.none { it.local }, onBack = onBack, listState = listState) { spec ->
            item { WearPageHeader(spec, null, stringResource(R.string.settings_profile_template)) }
            message?.let { item { WearInfoCard(spec) { Text(it) } } }
            item { WearActionButton(spec, Icons.TwoTone.Add, stringResource(R.string.app_profile_template_create), {
                creation = true; readOnly = false; selected = ""; editRequest++
            }) }
            item { WearActionButton(spec, Icons.TwoTone.Description, stringResource(R.string.wear_online_templates), {
                message = null; online = true; onlineSelection = null
                viewModel.dispatch(TemplateUiAction.BrowseOnline)
            }) }
            item { WearActionButton(spec, Icons.TwoTone.ContentPaste, stringResource(R.string.app_profile_import_from_clipboard), {
                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (text.isNullOrBlank()) message = emptyClipboard else viewModel.dispatch(TemplateUiAction.Import(text))
            }) }
            item { WearActionButton(spec, Icons.TwoTone.ContentCopy, exported, { viewModel.dispatch(TemplateUiAction.Export) }) }
            LazySegmentedColumn(state.templateList.filter { it.local }, { it.id }) { template ->
                WearSettingsJumpPageWidget(spec, template.name.ifBlank { template.id }, {
                    creation = false; readOnly = !template.local; selected = template.id; editRequest++
                }, icon = Icons.TwoTone.Description)
            }
        }
    }
}

@Composable
internal fun WearTemplateEditor(id: String, readOnly: Boolean, creation: Boolean, request: Int,
    onBack: () -> Unit, onSaved: () -> Unit) {
    val key = "wear-template-$id-$request"
    val viewModel = koinViewModel<TemplateEditorViewModel>(viewModelStoreOwner = rememberWearPageViewModelOwner(key),
        parameters = { parametersOf(id, readOnly, creation) })
    ReleaseWearPageViewModels(key, remember(viewModel) { viewModel.state.map { it.pendingWrites > 0 } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failed = stringResource(R.string.app_profile_template_save_failed)
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) { viewModel.events.collect { event -> when (event) {
        TemplateEditorUiEvent.Deleted, TemplateEditorUiEvent.Saved -> onSaved()
        is TemplateEditorUiEvent.Error -> message = failed
    } } }
    val template = state.template
    val fields = listOf(R.string.app_profile_template_id, R.string.app_profile_template_name,
        R.string.app_profile_template_description, R.string.module_author, R.string.wear_uid, R.string.wear_gid,
        R.string.profile_namespace, R.string.profile_groups, R.string.profile_capabilities,
        R.string.profile_selinux_context, R.string.profile_selinux_rules, R.string.profile_flags)
    var input by rememberSaveable { mutableIntStateOf(0) }
    val delete = rememberWearConfirmDialog(stringResource(R.string.app_profile_template_delete), stringResource(R.string.confirm_delete)) {
        viewModel.dispatch(TemplateEditorUiAction.Delete)
    }
    WearPageTransition(input.toString(), if (input == 0) 0 else 1) { route ->
        val shownInput = route.toInt()
        if (shownInput != 0) {
            WearSubPage({ input = 0 }) {
                WearTextInputPage(stringResource(shownInput), template.field(shownInput), multiline = shownInput == R.string.profile_selinux_rules) { text ->
                    runCatching { template.withField(shownInput, text) }.fold(
                        onSuccess = { viewModel.dispatch(TemplateEditorUiAction.Update(it)) }, onFailure = { message = failed })
                    input = 0
                }
            }
        } else WearList(isLoading = state.loading, onBack = onBack) { spec ->
            item { WearPageHeader(spec, null, stringResource(if (readOnly) R.string.app_profile_template_view else R.string.app_profile_template_edit)) }
            if (state.loadFailure != null) item { WearStatusItem(spec, Icons.TwoTone.Error, failed, tone = WearStatusTone.ERROR) }
            message?.let { item { WearInfoCard(spec) { Text(it) } } }
            if (state.loadFailure == null) {
                // A TransformingLazyColumn item places only its last child, so the value is the widget's description.
                fields.forEach { field -> item {
                    WearSettingsJumpPageWidget(spec, stringResource(field), { input = field },
                        icon = templateFieldIcon(field),
                        enabled = !readOnly && (field != R.string.app_profile_template_id || creation),
                        description = template.field(field).ifEmpty { null })
                } }
                if (!readOnly) {
                    item { WearActionButton(spec, Icons.TwoTone.Save, stringResource(R.string.app_profile_template_save), { viewModel.dispatch(TemplateEditorUiAction.Save) }) }
                    if (!creation) item { WearActionButton(spec, Icons.TwoTone.Delete, stringResource(R.string.app_profile_template_delete), delete::show) }
                }
            }
        }
    }
}

private fun templateFieldIcon(field: Int) = when (field) {
    R.string.app_profile_template_id, R.string.wear_uid, R.string.wear_gid -> Icons.TwoTone.Numbers
    R.string.app_profile_template_name -> Icons.TwoTone.Edit
    R.string.app_profile_template_description -> Icons.TwoTone.Description
    R.string.module_author -> Icons.TwoTone.Person
    R.string.profile_namespace -> Icons.TwoTone.Storage
    R.string.profile_groups -> Icons.TwoTone.Group
    R.string.profile_flags -> Icons.TwoTone.Flag
    else -> Icons.TwoTone.Security
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
        else -> copy(flags = numbers().onEach { require(it in 0 until com.resukisu.resukisu.Natives.Profile.RootProfileFlag.entries.size) })
    }
}
