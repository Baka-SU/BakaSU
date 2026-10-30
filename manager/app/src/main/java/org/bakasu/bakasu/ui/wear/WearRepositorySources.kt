package org.bakasu.bakasu.ui.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.RepositoryApiType
import org.bakasu.bakasu.domain.model.RepositoryFailure
import org.bakasu.bakasu.ui.viewmodel.RepositorySourcesViewModel
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearDetailField
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearErrorDialog
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearSubPage
import org.bakasu.bakasu.ui.wear.component.WearTextInputPage
import org.bakasu.bakasu.ui.wear.component.rememberWearConfirmDialog
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearChoicePage
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.koin.compose.viewmodel.koinViewModel

@Composable
private fun wearRepositoryError(failure: RepositoryFailure?): String? {
    if (failure == null) return null
    val reason = stringResource(failure.reason.error)
    return failure.httpStatus?.let { stringResource(R.string.repo_error_http, reason, it) } ?: reason
}

/** The same source repository and validation as the phone, presented with Wear settings rows. */
@Composable
internal fun WearRepositorySourcesPage(onBack: () -> Unit, onAdd: () -> Unit, onSource: (String) -> Unit) {
    val viewModel = koinViewModel<RepositorySourcesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    WearErrorDialog(wearRepositoryError(state.error), viewModel::clearError)
    WearList(onBack = onBack, isBusy = state.busy) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.repo_manage)) }
        item { WearActionButton(spec, Icons.Rounded.Add, stringResource(R.string.repo_add_repository), onAdd, enabled = !state.busy) }
        LazySegmentedColumn(state.sources, { it.url }) { source ->
            WearSettingsJumpPageWidget(
                spec,
                source.displayName,
                { onSource(source.url) },
                icon = Icons.Rounded.Cloud,
                description = if (source.isBuiltIn) stringResource(R.string.repo_built_in) else source.url,
            )
        }
    }
}

@Composable
internal fun WearRepositorySourcePage(url: String, selectedUrl: String?, onBack: () -> Unit, onSelect: (String) -> Unit) {
    val viewModel = koinViewModel<RepositorySourcesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val source = state.sources.firstOrNull { it.url == url }
    var editing by rememberSaveable { mutableStateOf(false) }
    WearErrorDialog(wearRepositoryError(state.error), viewModel::clearError)
    LaunchedEffect(source) { if (source == null) onBack() }
    if (source == null) return
    val remove = rememberWearConfirmDialog(
        stringResource(R.string.repo_remove),
        stringResource(R.string.repo_remove_message, source.displayName) + "\n\n" + source.url,
        destructive = true,
    ) { if (!source.isBuiltIn) viewModel.remove(source) }
    if (editing) {
        WearSubPage({ editing = false }) {
            WearTextInputPage(stringResource(R.string.repo_display_name), source.customName) { name ->
                if (!source.isBuiltIn) viewModel.rename(source, name.take(100)) { editing = false }
            }
        }
        return
    }
    WearList(onBack = onBack, isBusy = state.busy) { spec ->
        item { WearPageHeader(spec, source.displayName) }
        item {
            WearInfoCard(spec) {
                WearDetailField(Icons.Rounded.Language, stringResource(R.string.repo_server_url), source.url)
                WearDetailField(Icons.Rounded.Tune, stringResource(R.string.repo_api_type), stringResource(source.apiType.resId))
                if (source.description.isNotBlank()) {
                    androidx.wear.compose.material3.Text(source.description)
                }
            }
        }
        item {
            WearActionButton(
                spec,
                Icons.Rounded.Check,
                stringResource(R.string.repo_select_repository),
                { onSelect(source.url) },
                enabled = !state.busy && selectedUrl != source.url,
            )
        }
        if (!source.isBuiltIn) {
            SegmentedColumn(listOf(R.string.repo_edit_name, R.string.repo_remove), { it }) { action ->
                WearSettingsJumpPageWidget(
                    spec,
                    stringResource(action),
                    { if (action == R.string.repo_edit_name) editing = true else remove.show() },
                    icon = if (action == R.string.repo_edit_name) Icons.Rounded.Edit else Icons.Rounded.Delete,
                    enabled = !state.busy,
                )
            }
        }
    }
}

private enum class RepositoryInput { Url, Api }

@Composable
internal fun WearAddRepositoryPage(onBack: () -> Unit) {
    val viewModel = koinViewModel<RepositorySourcesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf<RepositoryInput?>(null) }
    WearErrorDialog(wearRepositoryError(state.error), viewModel::clearError)
    LaunchedEffect(state.added?.url) {
        if (state.added != null) {
            viewModel.consumeAdded()
            onBack()
        }
    }
    val editing = input
    if (editing != null) {
        WearSubPage({ input = null }) {
            if (editing == RepositoryInput.Url) {
                WearTextInputPage(stringResource(R.string.repo_server_url), state.url) {
                    viewModel.setUrl(it.trim())
                    input = null
                }
            } else {
                WearChoicePage(stringResource(R.string.repo_api_type), RepositoryApiType.entries.map { it.name to stringResource(it.resId) }, state.apiType.name, { input = null }) {
                    viewModel.setApiType(RepositoryApiType.valueOf(it))
                    input = null
                }
            }
        }
        return
    }
    WearList(
        onBack = onBack,
        isLoading = state.busy,
        onConfirm = { viewModel.add() },
        confirmLabel = stringResource(R.string.add),
        confirmEnabled = state.url.isNotBlank() && !state.busy,
    ) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.repo_add_repository)) }
        SegmentedColumn(listOf(RepositoryInput.Url, RepositoryInput.Api), { it }) { field ->
            WearSettingsJumpPageWidget(
                spec,
                stringResource(if (field == RepositoryInput.Url) R.string.repo_server_url else R.string.repo_api_type),
                { input = field },
                icon = if (field == RepositoryInput.Url) Icons.Rounded.Language else Icons.Rounded.Tune,
                description = if (field == RepositoryInput.Url) state.url else stringResource(state.apiType.resId),
            )
        }
    }
}
