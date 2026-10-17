package org.bakasu.bakasu.ui.screen.moduleRepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DeleteOutline
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import org.bakasu.bakasu.ui.component.FilledTonalLoadingButton
import org.bakasu.bakasu.ui.component.HorizontalPagerWithInteraction
import org.bakasu.bakasu.ui.component.NetworkRefreshContent
import org.bakasu.bakasu.ui.component.RepositoryEmptyContent
import org.bakasu.bakasu.ui.component.RepositoryPageScaffold
import org.bakasu.bakasu.ui.component.rememberConfirmDialog
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.component.rememberRepositoryScrollBehavior
import org.bakasu.bakasu.ui.component.repositoryErrorText
import org.bakasu.bakasu.ui.component.settings.SettingsBaseWidget
import org.bakasu.bakasu.ui.component.settings.lazySegmentColumn
import org.bakasu.bakasu.ui.navigation.LocalNavigator
import org.bakasu.bakasu.ui.theme.blurSource
import org.bakasu.bakasu.ui.viewmodel.RepositorySourcesViewModel
import org.koin.compose.viewmodel.koinViewModel

internal fun RepositorySource.shortUrl() = url.toHttpUrlOrNull()?.let {
    it.host + it.encodedPath.trimEnd('/')
} ?: url

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddRepositoryScreen() {
    val viewModel = koinViewModel<RepositorySourcesViewModel>(key = "add-repository")
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val pager = rememberPagerState(initialPage = 1) { 2 }
    val discoverScroll = rememberLazyListState()
    val customScroll = rememberLazyListState()
    val scrollBehavior = rememberRepositoryScrollBehavior(pager.currentPage)
    val scope = rememberCoroutineScope()
    val titles = listOf(stringResource(R.string.repo_discover), stringResource(R.string.repo_server_settings))
    val apiTypes = listOf(RepositoryApiType.KERNEL_SU, RepositoryApiType.MMRL)
    val apiTypeLabels = listOf(stringResource(R.string.repo_api_kernel_su), stringResource(R.string.repo_api_mmrl))
    var apiTypeExpanded by remember { mutableStateOf(false) }
    val configuredSourcesTitle = stringResource(R.string.repo_configured_sources)
    LaunchedEffect(Unit) { viewModel.loadDiscovery() }
    LaunchedEffect(state.added?.url) {
        if (state.added != null) {
            viewModel.consumeAdded()
            navigator.pop()
        }
    }
    RepositoryPageScaffold(
        title = stringResource(R.string.repo_configure),
        scrollBehavior = scrollBehavior,
        topBarContent = {
            PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = Color.Transparent) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = pager.currentPage == index,
                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPagerWithInteraction(
            pager,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).blurSource(),
        ) { page ->
            if (page == 0 && state.discovering && state.discovered.isEmpty()) {
                NetworkRefreshContent(
                    offline = false,
                    onRetry = { viewModel.loadDiscovery() },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (page == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = discoverScroll,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.discovering) {
                        item {
                            NetworkRefreshContent(
                                offline = false,
                                onRetry = { viewModel.loadDiscovery(force = true) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            )
                        }
                    }
                    state.discoveryError?.let { error ->
                        item {
                            RepositoryEmptyContent(
                                title = repositoryErrorText(error),
                                description = "",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                action = stringResource(R.string.network_retry),
                                onAction = { viewModel.loadDiscovery(force = true) },
                            )
                        }
                    }
                    state.error?.let { error -> item { Text(repositoryErrorText(error), color = MaterialTheme.colorScheme.error) } }
                    if (!state.discovering && state.discoveryError == null && state.discovered.isEmpty()) {
                        item {
                            RepositoryEmptyContent(
                                stringResource(R.string.repo_discovery_empty),
                                "",
                                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            )
                        }
                    }
                    items(state.discovered, key = { it.url }) { source ->
                        val added = state.sources.any { it.url == source.url }
                        val adding = state.addingUrl == source.url
                        val description = source.description.takeIf(String::isNotBlank)
                        CatalogCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    CatalogCardHeading(source.name)
                                    Text(
                                        source.shortUrl(),
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (added && !adding) {
                                    FilledTonalButton(onClick = {}, enabled = false) {
                                        Text(stringResource(R.string.repo_added))
                                    }
                                } else {
                                    FilledTonalLoadingButton(
                                        text = stringResource(R.string.add),
                                        loading = adding,
                                        onClick = { viewModel.add(source.url, source, RepositoryApiType.MMRL) },
                                        enabled = !state.busy,
                                    )
                                }
                            }
                            if (description != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            source.moduleCount?.let { count ->
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(thickness = Dp.Hairline)
                                Text(
                                    pluralStringResource(R.plurals.repo_module_count, count, count),
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = customScroll,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.repo_custom_repository),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExposedDropdownMenuBox(
                                expanded = apiTypeExpanded && !state.busy,
                                onExpandedChange = { apiTypeExpanded = it && !state.busy },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedTextField(
                                    value = apiTypeLabels[apiTypes.indexOf(state.apiType)],
                                    onValueChange = {},
                                    readOnly = true,
                                    singleLine = true,
                                    enabled = !state.busy,
                                    label = { Text(stringResource(R.string.repo_api_type)) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = apiTypeExpanded && !state.busy)
                                    },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !state.busy),
                                )
                                ExposedDropdownMenu(
                                    expanded = apiTypeExpanded && !state.busy,
                                    onDismissRequest = { apiTypeExpanded = false },
                                    containerColor = MenuDefaults.groupStandardContainerColor,
                                    shape = MenuDefaults.standaloneGroupShape,
                                ) {
                                    apiTypes.forEachIndexed { index, type ->
                                        SelectableDropdownMenuItem(
                                            selected = state.apiType == type,
                                            onClick = {
                                                viewModel.setApiType(type)
                                                apiTypeExpanded = false
                                            },
                                            text = { Text(apiTypeLabels[index]) },
                                            shapes = MenuDefaults.itemShape(index = index, count = apiTypes.size),
                                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = state.url,
                                onValueChange = viewModel::setUrl,
                                label = { Text(stringResource(R.string.repo_server_url)) },
                                placeholder = {
                                    Text(
                                        stringResource(
                                            if (state.apiType == RepositoryApiType.KERNEL_SU) {
                                                R.string.repo_url_kernel_su_hint
                                            } else {
                                                R.string.repo_url_mmrl_hint
                                            },
                                        ),
                                    )
                                },
                                singleLine = true,
                                enabled = !state.busy,
                                isError = state.error != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (state.url.isNotBlank() && !state.busy) viewModel.add()
                                }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            state.error?.let { Text(repositoryErrorText(it), color = MaterialTheme.colorScheme.error) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                FilledTonalLoadingButton(
                                    text = stringResource(R.string.add),
                                    loading = state.addingUrl == state.url,
                                    onClick = { viewModel.add() },
                                    enabled = state.url.isNotBlank() && !state.busy,
                                )
                            }
                        }
                    }
                    lazySegmentColumn(
                        state.sources,
                        title = configuredSourcesTitle,
                        noHorizontalPadding = true,
                        key = { _, source -> source.url },
                    ) { _, source ->
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
    }
}

@Composable
internal fun RepositorySourceItem(
    source: RepositorySource,
    busy: Boolean,
    failed: Boolean,
    onOpen: (() -> Unit)? = null,
    onRename: (String, () -> Unit) -> Unit,
    onRemove: () -> Unit,
) {
    SettingsBaseWidget(
        title = null,
        iconPlaceholder = false,
        onClick = onOpen?.let { open -> { _ -> open() } },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        foreContent = {
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
        },
        descriptionColumnContent = {
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
        },
    )
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
