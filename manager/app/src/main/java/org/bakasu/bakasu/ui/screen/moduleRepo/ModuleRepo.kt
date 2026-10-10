package org.bakasu.bakasu.ui.screen.moduleRepo

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.Star
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material.icons.twotone.WebAsset
import androidx.compose.material3.Button
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.CatalogAuthor
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.model.RepositoryError
import org.bakasu.bakasu.ui.activity.PermissionRequestInterface
import org.bakasu.bakasu.ui.component.CatalogCard
import org.bakasu.bakasu.ui.component.CatalogCardHeading
import org.bakasu.bakasu.ui.component.NetworkRefreshContent
import org.bakasu.bakasu.ui.component.RepositoryEmptyContent
import org.bakasu.bakasu.ui.component.SearchAppBar
import org.bakasu.bakasu.ui.component.SwipeableSnackbarHost
import org.bakasu.bakasu.ui.component.moduleAssetDetails
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.component.rememberRepositoryInstallDialog
import org.bakasu.bakasu.ui.component.rememberSearchAppBarScrollBehavior
import org.bakasu.bakasu.ui.component.repositoryErrorText
import org.bakasu.bakasu.ui.component.settings.LocalSegmentedItemShape
import org.bakasu.bakasu.ui.component.settings.lazySegmentColumn
import org.bakasu.bakasu.ui.navigation.LocalNavigator
import org.bakasu.bakasu.ui.navigation.Navigator
import org.bakasu.bakasu.ui.navigation.Route
import org.bakasu.bakasu.ui.screen.LabelText
import org.bakasu.bakasu.ui.theme.blurSource
import org.bakasu.bakasu.ui.theme.renderBackgroundBlur
import org.bakasu.bakasu.ui.util.LocalPermissionRequestInterface
import org.bakasu.bakasu.ui.util.LocalSnackbarHost
import org.bakasu.bakasu.ui.util.adaptiveScaffoldWindowInsets
import org.bakasu.bakasu.ui.viewmodel.CatalogContent
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoUiAction
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoUiState
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoViewModel
import org.bakasu.bakasu.ui.viewmodel.RepositorySourcesViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * @author AlexLiuDev233
 * @date 2025/12/6
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModuleRepoScreen(repositoryUrl: String? = null) {
    val navigator = LocalNavigator.current
    val viewModel = koinViewModel<ModuleRepoViewModel>(
        key = repositoryUrl ?: "repository-home",
        parameters = { parametersOf(repositoryUrl) },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sourcesViewModel = koinViewModel<RepositorySourcesViewModel>(key = "repository-home-actions")
    val sourcesState by sourcesViewModel.state.collectAsStateWithLifecycle()
    val layoutDirection = LocalLayoutDirection.current
    val confirmDownload = rememberRepositoryInstallDialog()
    val snackBarHost = LocalSnackbarHost.current
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState),
    )
    val currentModuleForChooseDialog = remember { mutableStateOf<CatalogModule?>(null) }
    fun downloadSelectedAsset(catalogId: String, asset: ModuleReleaseAsset) {
        val module = viewModel.resolveAsset(catalogId, asset) ?: return
        confirmDownload(module, asset) { viewModel.resolveAsset(catalogId, asset) != null }
    }
    val chooseDialog = rememberCustomDialog({ dismiss ->
        val module = uiState.modules.firstOrNull { it.catalogId == currentModuleForChooseDialog.value?.catalogId }
        if (module == null) {
            LaunchedEffect(Unit) { dismiss() }
        } else {
            ChooseDialogContent(module, onSelect = { downloadSelectedAsset(module.catalogId, it) }, dismiss = dismiss)
        }
    })
    fun downloadSelectedModule(selected: CatalogModule) {
        val module = viewModel.resolveModule(selected.catalogId) ?: return
        val assets = module.latestAsset?.assets.orEmpty()
        if (assets.size == 1) {
            downloadSelectedAsset(module.catalogId, assets.single())
        } else if (assets.size > 1) {
            currentModuleForChooseDialog.value = module
            chooseDialog.show()
        }
    }
    val repositoryListState = rememberLazyListState()
    val moduleListState = rememberLazyListState()
    var showDropdown by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    val refreshModules = { viewModel.dispatch(ModuleRepoUiAction.Refresh) }

    LaunchedEffect(viewModel) { viewModel.refresh(force = false) }

    Scaffold(
        topBar = {
            SearchAppBar(
                title = uiState.sources.firstOrNull { it.url == repositoryUrl }?.displayName ?: stringResource(R.string.module_repo),
                searchText = uiState.search,
                onSearchTextChange = { query ->
                    viewModel.dispatch(ModuleRepoUiAction.Search(query))
                },
                dropdownContent = {
                    if (repositoryUrl != null || uiState.search.isNotBlank()) {
                        IconButton(
                            onClick = { showDropdown = true },
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.MoreVert,
                                contentDescription = stringResource(id = R.string.settings),
                            )

                            ModuleRepoDropdown(
                                expanded = showDropdown,
                                onDismissRequest = { showDropdown = false },
                                viewModel = viewModel,
                                uiState = uiState,
                            )
                        }
                    }
                },
                onBackClick = {
                    navigator.pop()
                },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = stringResource(R.string.search_modules),
                preserveSearchOnNavigation = true,
                expandOnActivation = true,
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        floatingActionButton = {
            if (repositoryUrl == null && uiState.search.isBlank()) {
                ExtendedFloatingActionButton(
                    onClick = { navigator.push(Route.AddRepository) },
                    icon = { Icon(Icons.TwoTone.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.repo_add_repository)) },
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier.fillMaxSize().blurSource(),
            state = pullRefreshState,
            isRefreshing = uiState.isPullRefreshing,
            onRefresh = {
                viewModel.dispatch(ModuleRepoUiAction.PullRefresh)
            },
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.isPullRefreshing,
                    modifier = Modifier
                        .padding(top = innerPadding.calculateTopPadding())
                        .align(Alignment.TopCenter),
                )
            },
        ) {
            when (uiState.content) {
                CatalogContent.REPOSITORIES -> LazyColumn(
                    state = repositoryListState,
                    contentPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                        top = innerPadding.calculateTopPadding(),
                        end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 88.dp,
                    ),
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                ) {
                    sourcesState.error?.let { error ->
                        item {
                            Text(repositoryErrorText(error), Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    lazySegmentColumn(uiState.sources, noHorizontalPadding = true, itemSpacing = 12.dp, key = { _, source -> source.url }) { _, source ->
                        CompositionLocalProvider(LocalSegmentedItemShape provides RoundedCornerShape(20.dp)) {
                            RepositorySourceItem(
                                source = source,
                                busy = sourcesState.busy,
                                failed = source.url in uiState.failures,
                                onOpen = { navigator.push(Route.RepositoryModules(source.url)) },
                                onRename = { name, onSaved -> sourcesViewModel.rename(source, name, onSaved) },
                                onRemove = { sourcesViewModel.remove(source) },
                            )
                        }
                    }
                }

                CatalogContent.MODULES -> LazyColumn(
                    state = moduleListState,
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp, innerPadding.calculateTopPadding(), 16.dp, innerPadding.calculateBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val failures = uiState.sources.filter { repositoryUrl == null || it.url == repositoryUrl }
                        .mapNotNull { uiState.failures[it.url] }.distinct()
                    if (repositoryUrl != null && failures.isNotEmpty() && !uiState.isRefreshing) {
                        item {
                            TextButton(onClick = refreshModules) { Text(failures.map { repositoryErrorText(it) }.joinToString("\n")) }
                        }
                    }
                    items(uiState.modules, key = { it.catalogId }) { module ->
                        OnlineModuleItem(
                            module = module,
                            showSource = repositoryUrl == null,
                            onOpen = {
                                viewModel.resolveModule(module.catalogId)?.let {
                                    navigator.push(Route.ModuleRepoDetail(it.moduleId, it.repositoryUrl))
                                }
                            },
                            onDownload = if (module.latestAsset?.assets?.isNotEmpty() == true) ({ downloadSelectedModule(module) }) else null,
                        )
                    }
                }

                CatalogContent.FAILED -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    val failures = uiState.sources.filter { repositoryUrl == null || it.url == repositoryUrl }
                        .mapNotNull { uiState.failures[it.url] }.distinct()
                    if (failures.all { it.reason == RepositoryError.OFFLINE }) {
                        NetworkRefreshContent(offline = true, onRetry = refreshModules, modifier = Modifier.fillMaxSize())
                    } else {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .renderBackgroundBlur(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(12.dp))

                            Text(
                                text = stringResource(R.string.internal_server_error),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleMediumEmphasized,
                            )
                            Spacer(modifier = Modifier.height(1.dp))

                            Text(
                                text = failures.map { repositoryErrorText(it) }.joinToString("\n"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMediumEmphasized,
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                contentPadding = PaddingValues(horizontal = 50.dp),
                                onClick = refreshModules,
                            ) {
                                Text(text = stringResource(R.string.network_retry))
                            }
                        }
                    }
                }

                else -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    if (uiState.content == CatalogContent.LOADING && !uiState.isPullRefreshing) {
                        NetworkRefreshContent(offline = false, onRetry = refreshModules, modifier = Modifier.fillMaxSize())
                    } else if (uiState.content != CatalogContent.LOADING) {
                        val content = uiState.content
                        val title = when (content) {
                            CatalogContent.SOURCE_UNAVAILABLE -> R.string.repo_module_unavailable
                            CatalogContent.SEARCH_EMPTY -> R.string.search_no_any_match
                            else -> R.string.repo_source_empty
                        }
                        RepositoryEmptyContent(
                            title = stringResource(title),
                            description = "",
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModuleRepoDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: ModuleRepoViewModel,
    uiState: ModuleRepoUiState,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShapes(),
        ) {
            CheckableDropdownMenuItem(
                checked = uiState.sortStargazerCountFirst,
                onCheckedChange = {
                    viewModel.dispatch(ModuleRepoUiAction.SetStarsFirst(it))
                },
                text = { Text(stringResource(R.string.module_sort_star_first)) },
                shapes = MenuDefaults.itemShape(
                    index = 0,
                    count = 1,
                ),
            )
        }
    }
}

@Composable
fun OnlineModuleItem(
    module: CatalogModule,
    onOpen: () -> Unit,
    onDownload: (() -> Unit)? = null,
    showSource: Boolean = true,
) {
    CatalogCard(
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val moduleVersion = stringResource(id = R.string.module_version)
            val moduleAuthor = stringResource(id = R.string.module_author)

            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                CatalogCardHeading(module.moduleName) {
                    if (module.stargazerCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = module.stargazerCount.toString(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }

                Text(
                    text = "$moduleVersion: ${module.latestRelease} (${module.latestVersionCode})",
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                )

                Text(
                    text = "$moduleAuthor: ${module.authors}",
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                )
                if (showSource) {
                    Text(
                        text = stringResource(R.string.repo_source_label, module.repositoryName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = module.summary,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            fontWeight = MaterialTheme.typography.bodySmall.fontWeight,
            overflow = TextOverflow.Ellipsis,
            maxLines = 4,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            LabelText(
                label = module.moduleId,
                containerColor = MaterialTheme.colorScheme.primary,
            )
            if (module.metamodule) {
                LabelText(
                    label = stringResource(R.string.module_meta_label),
                    containerColor = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (module.installed) {
                LabelText(
                    label = stringResource(R.string.installed),
                    containerColor = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider(thickness = Dp.Hairline)

        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterVertically)) {
                Spacer(modifier = Modifier.weight(1f))
                FilledTonalButton(
                    modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                    onClick = onOpen,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = 7.dp,
                        end = 12.dp,
                        bottom = 7.dp,
                    ),
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.TwoTone.WebAsset,
                        contentDescription = null,
                    )
                }
                Spacer(Modifier.width(10.dp))

                if (onDownload != null) {
                    FilledTonalButton(
                        modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                        onClick = onDownload,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 7.dp,
                            end = 12.dp,
                            bottom = 7.dp,
                        ),
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.TwoTone.Download,
                            contentDescription = stringResource(R.string.install),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChooseDialogContent(
    module: CatalogModule?,
    onSelect: (ModuleReleaseAsset) -> Unit,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current
    if (module == null || module.latestAsset == null) {
        LaunchedEffect(Unit) { dismiss() }
        return
    }
    var selectedAsset by remember(module.catalogId, module.latestAsset) { mutableStateOf<ModuleReleaseAsset?>(null) }

    Dialog(
        onDismissRequest = { dismiss() },
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.assets_multiple_select_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(module.latestAsset.assets) { asset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape = RoundedCornerShape(24.dp))
                                .clickable { selectedAsset = asset }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedAsset == asset,
                                onClick = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            Column {
                                Text(
                                    text = asset.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                moduleAssetDetails(asset, R.string.assets_multiple_select_dialog_content_description)?.let { details ->
                                    Text(
                                        text = details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { dismiss() }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (selectedAsset == null) {
                                Toast.makeText(context, R.string.assets_multiple_select_dialog_warning, Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            selectedAsset?.let { selected ->
                                dismiss()
                                onSelect(selected)
                            }
                        },
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}

// 下面全是预览相关了

fun initFakeRepoModuleForPreview(): CatalogModule = CatalogModule(
    moduleId = "id",
    moduleName = "name",
    authors = "author",
    authorList = ArrayList<CatalogAuthor>().apply {
        add(
            CatalogAuthor(
                name = "name",
                link = "link",
            ),
        )
    },
    summary = "I am a test module and i do nothing but show a very long description",
    metamodule = true,
    stargazerCount = 1,
    updatedAt = "updateAt",
    createdAt = "createAt",
    latestRelease = "latestRelease",
    latestReleaseTime = "latestReleaseTime",
    latestVersionCode = 1,
    latestAsset = ModuleRelease(
        name = "name",
        tagName = "tagName",
        publishedAt = "publishedAt",
        assets = ArrayList<ModuleReleaseAsset>().apply {
            add(
                ModuleReleaseAsset(
                    name = "name",
                    downloadUrl = "downloadUrl",
                    size = 0,
                    downloadCount = 0,
                ),
            )
            add(
                ModuleReleaseAsset(
                    name = "name2",
                    downloadUrl = "downloadUrl2",
                    size = 0,
                    downloadCount = 0,
                ),
            )
        },
    ),
    installed = true,
    sourceUrl = "Source URL",
    releases = emptyList(),
    repositoryUrl = "https://example.com/",
)

@Preview(locale = "en")
@Composable
private fun OnlineModuleItemPreview() {
    CompositionLocalProvider(
        LocalNavigator provides Navigator(Route.ModuleRepo),
        LocalPermissionRequestInterface provides object : PermissionRequestInterface {
            override fun requestPermission(
                permission: String,
                callback: (Boolean) -> Unit,
                requestDescription: String,
            ) {
            }

            override fun requestPermissions(
                permissions: Array<String>,
                callback: (Map<String, @JvmSuppressWildcards Boolean>) -> Unit,
                requestDescription: Map<String, String>,
            ) {
            }
        },
    ) {
        OnlineModuleItem(
            initFakeRepoModuleForPreview(),
            onOpen = {},
            onDownload = {},
        )
    }
}

@Preview(locale = "zh-rCN", showBackground = true)
@Composable
private fun ChooseDialogPreview() {
    val currentModuleForChooseDialog =
        remember { mutableStateOf<CatalogModule?>(initFakeRepoModuleForPreview()) }

    CompositionLocalProvider(
        LocalNavigator provides Navigator(Route.ModuleRepo),
        LocalPermissionRequestInterface provides object : PermissionRequestInterface {
            override fun requestPermission(
                permission: String,
                callback: (Boolean) -> Unit,
                requestDescription: String,
            ) {
            }

            override fun requestPermissions(
                permissions: Array<String>,
                callback: (Map<String, @JvmSuppressWildcards Boolean>) -> Unit,
                requestDescription: Map<String, String>,
            ) {
            }
        },
    ) {
        ChooseDialogContent(
            currentModuleForChooseDialog.value,
            onSelect = {},
        ) {}
    }
}
