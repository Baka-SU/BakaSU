package org.bakasu.bakasu.ui.screen.moduleRepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.DeleteOutline
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.RepositoryApiType
import org.bakasu.bakasu.domain.model.RepositorySource
import org.bakasu.bakasu.ui.component.CatalogCard
import org.bakasu.bakasu.ui.component.CatalogCardHeading
import org.bakasu.bakasu.ui.component.ConfirmResult
import org.bakasu.bakasu.ui.component.SwipeableSnackbarHost
import org.bakasu.bakasu.ui.component.rememberConfirmDialog
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.component.repositoryErrorText
import org.bakasu.bakasu.ui.component.settings.AppBackButton
import org.bakasu.bakasu.ui.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.component.settings.SettingsDropdownWidget
import org.bakasu.bakasu.ui.component.settings.SettingsTextFieldWidget
import org.bakasu.bakasu.ui.navigation.LocalNavigator
import org.bakasu.bakasu.ui.theme.CardConfig
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.theme.blurEffect
import org.bakasu.bakasu.ui.theme.blurSource
import org.bakasu.bakasu.ui.util.LocalBackgroundBlurProvided
import org.bakasu.bakasu.ui.util.LocalSnackbarHost
import org.bakasu.bakasu.ui.util.adaptiveScaffoldWindowInsets
import org.bakasu.bakasu.ui.viewmodel.RepositorySourcesViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

internal fun RepositorySource.shortUrl() = url.toHttpUrlOrNull()?.let {
    it.host + it.encodedPath.trimEnd('/')
} ?: url

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddRepositoryScreen() {
    val viewModel = koinViewModel<RepositorySourcesViewModel>(key = "add-repository")
    val state by viewModel.state.collectAsStateWithLifecycle()
    val customScroll = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(
            initialHeightOffset = -154f,
            initialHeightOffsetLimit = -154f, // from debugger
        ),
    )
    val apiTypes = listOf(RepositoryApiType.KERNEL_SU, RepositoryApiType.MMRL)
    val apiTypeLabels = apiTypes.map { stringResource(it.resId) }
    val addForm = rememberCustomDialog { dismiss ->
        val urlField = rememberTextFieldState(initialText = state.url)
        LaunchedEffect(urlField) {
            snapshotFlow { urlField.text.toString() }.collect { value ->
                if (value != state.url) viewModel.setUrl(value)
            }
        }
        val dismissAddForm = {
            if (!state.busy) {
                viewModel.clearError()
                dismiss()
            }
        }
        AlertDialog(
            onDismissRequest = dismissAddForm,
            title = { Text(stringResource(R.string.repo_add_repository)) },
            text = {
                CompositionLocalProvider(LocalBackgroundBlurProvided provides true) {
                    SegmentedColumn(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        item {
                            SettingsDropdownWidget(
                                title = stringResource(R.string.repo_api_type),
                                description = apiTypeLabels[apiTypes.indexOf(state.apiType)],
                                iconPlaceholder = false,
                                enabled = !state.busy,
                                renderBackgroundBlur = false,
                                choice = apiTypes.indexOf(state.apiType),
                                data = apiTypeLabels,
                                onChoiceChange = { viewModel.setApiType(apiTypes[it]) },
                            )
                        }
                        item {
                            SettingsTextFieldWidget(
                                state = urlField,
                                title = stringResource(R.string.repo_server_url),
                                error = state.error?.let { repositoryErrorText(it) }.orEmpty(),
                                enabled = !state.busy,
                                renderBackgroundBlur = false,
                                lineLimits = TextFieldLineLimits.SingleLine,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                                onKeyboardAction = {
                                    if (urlField.text.isNotBlank() && !state.busy) viewModel.add(url = urlField.text.toString())
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                val loading = state.addingUrl == state.url
                FilledTonalButton(onClick = { viewModel.add() }, modifier = Modifier, enabled = state.url.isNotBlank() && !state.busy && !loading) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = stringResource(R.string.add), modifier = Modifier.alpha(if (loading) 0f else 1f))
                        if (loading) {
                            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                                LoadingIndicator(Modifier.size(20.dp))
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = dismissAddForm, enabled = !state.busy) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    LaunchedEffect(state.added?.url) {
        if (state.added != null) {
            viewModel.consumeAdded()
            if (addForm.isShown) {
                addForm.hide()
                viewModel.setUrl("")
            }
        }
    }
    val navigator = LocalNavigator.current
    val theme = koinInject<ThemeConfig>()
    val cards = koinInject<CardConfig>()
    val barColor = if (theme.isEnableBlur) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(cards.cardAlpha)
    }
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = {
                    Text(
                        text = stringResource(R.string.repo_configure),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = barColor,
                    scrolledContainerColor = barColor,
                ),
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        snackbarHost = { SwipeableSnackbarHost(hostState = LocalSnackbarHost.current) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().blurSource(),
            state = customScroll,
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "add-repository") {
                if (!addForm.isShown) {
                    state.error?.let { error ->
                        Text(
                            repositoryErrorText(error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
                FilledTonalButton(
                    onClick = {
                        viewModel.clearError()
                        addForm.show()
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.TwoTone.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.repo_add_repository), modifier = Modifier.padding(start = 8.dp))
                }
            }
            items(state.sources, key = { it.url }) { source ->
                RepositorySourceItem(
                    source = source,
                    busy = state.busy,
                    failed = state.failures.containsKey(source.url),
                    onRename = { name, onSaved -> viewModel.rename(source, name, onSaved) },
                    onRemove = { viewModel.remove(source) },
                )
            }
        }
    }
}

@Composable
internal fun RepositorySourceItem(
    source: RepositorySource,
    busy: Boolean,
    failed: Boolean,
    onRename: (String, () -> Unit) -> Unit,
    onRemove: () -> Unit,
) {
    CatalogCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                CatalogCardHeading(source.displayName)
                Text(
                    source.shortUrl(),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!source.isBuiltIn) {
                RepositoryActions(source, busy, onRename, onRemove)
            }
        }
        if (source.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                source.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (failed) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.repo_refresh_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (source.moduleCount != null || source.isBuiltIn) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = Dp.Hairline)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    source.moduleCount?.let { count ->
                        Text(
                            pluralStringResource(R.plurals.repo_module_count, count, count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (source.isBuiltIn) {
                    Text(
                        stringResource(R.string.repo_built_in),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RepositoryActions(
    source: RepositorySource,
    busy: Boolean,
    onRename: (String, () -> Unit) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val confirm = rememberConfirmDialog()
    val scope = rememberCoroutineScope()
    val removeTitle = stringResource(R.string.repo_remove)
    val removeMessage = stringResource(R.string.repo_remove_message, source.displayName) + "\n\n" + source.url
    val removeAction = stringResource(R.string.repo_remove_action)
    val cancel = stringResource(R.string.cancel)
    var name by rememberSaveable(source.url) { mutableStateOf(source.displayName) }
    val edit = rememberCustomDialog { dismiss ->
        AlertDialog(
            onDismissRequest = { if (!busy) dismiss() },
            title = { Text(stringResource(R.string.repo_edit_name)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    label = { Text(stringResource(R.string.repo_display_name)) },
                    supportingText = { Text(stringResource(R.string.repo_display_name_hint)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(name, dismiss) }, enabled = !busy) { Text(stringResource(R.string.repo_save_name)) }
            },
            dismissButton = {
                TextButton(onClick = dismiss, enabled = !busy) { Text(cancel) }
            },
        )
    }
    IconButton(onClick = { expanded = true }, enabled = !busy, modifier = Modifier.size(48.dp)) {
        Icon(Icons.TwoTone.MoreVert, contentDescription = stringResource(R.string.repo_actions))
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                SelectableDropdownMenuItem(
                    selected = false,
                    onClick = {
                        expanded = false
                        name = source.displayName
                        edit.show()
                    },
                    text = { Text(stringResource(R.string.repo_edit_name)) },
                    leadingIcon = { Icon(Icons.TwoTone.Edit, contentDescription = null) },
                    shapes = MenuDefaults.itemShape(0, 2),
                )
                SelectableDropdownMenuItem(
                    selected = false,
                    onClick = {
                        expanded = false
                        scope.launch {
                            if (confirm.isShown) return@launch
                            val result = confirm.awaitConfirm(removeTitle, removeMessage, confirm = removeAction, dismiss = cancel)
                            if (result == ConfirmResult.Confirmed) onRemove()
                        }
                    },
                    text = { Text(removeAction, color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.TwoTone.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    shapes = MenuDefaults.itemShape(1, 2),
                )
            }
        }
    }
}
