package org.bakasu.bakasu.ui.wear

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
    AnimatedContent(
        targetState = input,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val direction = if (targetState == null) -1 else 1
            (slideInHorizontally { direction * it / 2 } + fadeIn()) togetherWith
                (slideOutHorizontally { -direction * it / 2 } + fadeOut())
        },
        label = "wear-repository-input",
    ) { editing ->
        if (editing != null) {
            WearSubPage({ input = null }) {
                if (editing == RepositoryInput.Url) {
                    WearTextInputPage(
                        title = stringResource(R.string.repo_server_url),
                        initialValue = state.url,
                        autoFocus = true,
                        keyboardType = KeyboardType.Uri,
                        placeholder = stringResource(R.string.wear_repository_url_hint),
                    ) {
                        viewModel.setUrl(it.trim())
                        input = null
                    }
                } else {
                    WearChoicePage(
                        title = stringResource(R.string.repo_api_type),
                        choices = RepositoryApiType.entries.map { it.name to stringResource(it.resId) },
                        selected = state.apiType.name,
                        onBack = { input = null },
                        enabled = !state.busy,
                        icon = { Icons.Rounded.Cloud },
                    ) {
                        viewModel.setApiType(RepositoryApiType.valueOf(it))
                        input = null
                    }
                }
            }
        } else {
            WearList(
                onBack = onBack,
                isBusy = state.busy,
                isRefreshing = state.busy,
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
                        enabled = !state.busy,
                        description = if (field == RepositoryInput.Url) state.url.ifBlank { stringResource(R.string.wear_repository_url_hint) } else stringResource(state.apiType.resId),
                        descriptionMaxLines = if (field == RepositoryInput.Url) 3 else 2,
                    )
                }
            }
        }
    }
}
