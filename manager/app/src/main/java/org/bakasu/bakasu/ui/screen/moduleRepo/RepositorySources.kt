package org.bakasu.bakasu.ui.screen.moduleRepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.bakasu.bakasu.R
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
    val pager = rememberPagerState { 2 }
    val discoverScroll = rememberLazyListState()
    val customScroll = rememberScrollState()
    val scrollBehavior = rememberRepositoryScrollBehavior(pager.currentPage)
    val scope = rememberCoroutineScope()
    val titles = listOf(stringResource(R.string.repo_discover), stringResource(R.string.repo_custom_url))
    LaunchedEffect(Unit) { viewModel.loadDiscovery() }
    LaunchedEffect(state.added?.url) {
        if (state.added != null) {
            viewModel.consumeAdded()
            navigator.pop()
        }
    }
    RepositoryPageScaffold(
        title = stringResource(R.string.repo_add_repository),
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
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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
                        CatalogCard(shape = RoundedCornerShape(20.dp)) {
                            CatalogCardHeading(source.name, scrollTitle = false)
                            Text(
                                source.shortUrl(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (source.description.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    source.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Row(
                                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                if (added && !adding) {
                                    FilledTonalButton(onClick = {}, enabled = false) {
                                        Text(stringResource(R.string.repo_added))
                                    }
                                }
                                if (!added || adding) {
                                    FilledTonalLoadingButton(
                                        text = stringResource(R.string.add),
                                        loading = adding,
                                        onClick = { viewModel.add(source.url, source) },
                                        enabled = !state.busy,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(customScroll).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.url,
                        onValueChange = viewModel::setUrl,
                        label = { Text(stringResource(R.string.repo_url)) },
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
        }
    }
}

@Composable
internal fun RepositorySourceItem(
    source: RepositorySource,
    busy: Boolean,
    failed: Boolean,
    onOpen: () -> Unit,
    onRename: (String, () -> Unit) -> Unit,
    onRemove: () -> Unit,
) {
    SettingsBaseWidget(
        title = null,
        iconPlaceholder = false,
        onClick = { onOpen() },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        foreContent = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    source.displayName,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (source.isBuiltIn) {
                    Text(
                        stringResource(R.string.repo_built_in),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Box(Modifier.width(28.dp).height(24.dp).wrapContentSize(unbounded = true)) {
                        RepositoryActions(source, busy, onRename, onRemove)
                    }
                }
            }
        },
        descriptionColumnContent = {
            Spacer(Modifier.height(4.dp))
            Text(
                source.shortUrl(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (source.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    source.description,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (failed) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.repo_refresh_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
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
