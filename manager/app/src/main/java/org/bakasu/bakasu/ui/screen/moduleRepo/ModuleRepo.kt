package org.bakasu.bakasu.ui.screen.moduleRepo

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Star
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.CatalogAuthor
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.DownloadStatus
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.model.RepositoryError
import org.bakasu.bakasu.domain.usecase.ObserveAssetDownloadUseCase
import org.bakasu.bakasu.ui.activity.PermissionRequestInterface
import org.bakasu.bakasu.ui.component.CatalogCard
import org.bakasu.bakasu.ui.component.CatalogCardHeading
import org.bakasu.bakasu.ui.component.CatalogModuleIcon
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
    val snackBarHost = LocalSnackbarHost.current
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState),
    )
    val repositoryListState = rememberLazyListState()
    val moduleListState = rememberLazyListState()
    var showDropdown by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    val refreshModules = { viewModel.dispatch(ModuleRepoUiAction.Refresh) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirmInstall = rememberRepositoryInstallDialog()
    var resolvingModule by remember { mutableStateOf<String?>(null) }
    var assetSelection by remember { mutableStateOf<CatalogModule?>(null) }
    val selectAsset = rememberCustomDialog { dismiss ->
        val module = assetSelection
        if (module != null) {
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringResource(R.string.repo_select_install_asset)) },
                text = {
                    LazyColumn {
                        items(module.latestAsset?.assets.orEmpty()) { asset ->
                            TextButton(
                                onClick = {
                                    dismiss()
                                    confirmInstall(module, asset) { viewModel.resolveAsset(module.catalogId, asset) != null }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(asset.name)
                                    moduleAssetDetails(asset)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
    val quickInstall: (CatalogModule) -> Unit = { module ->
        if (resolvingModule == null) {
            resolvingModule = module.catalogId
            scope.launch {
                try {
                    val current = viewModel.resolveModuleForInstall(module.catalogId)
                    val assets = current?.latestAsset?.assets.orEmpty()
                    when {
                        current == null -> Toast.makeText(context, R.string.repo_detail_error, Toast.LENGTH_LONG).show()

                        assets.isEmpty() -> Toast.makeText(context, R.string.repo_no_releases, Toast.LENGTH_LONG).show()

                        assets.size == 1 -> confirmInstall(current, assets.single()) {
                            viewModel.resolveAsset(current.catalogId, assets.single()) != null
                        }

                        else -> {
                            assetSelection = current
                            selectAsset.show()
                        }
                    }
                } finally {
                    resolvingModule = null
                }
            }
        }
    }

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
                        CompositionLocalProvider(LocalSegmentedItemShape provides RoundedCornerShape(16.dp)) {
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
                    contentPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                        top = innerPadding.calculateTopPadding(),
                        end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            installedVersionCode = uiState.installedVersions[module.moduleId],
                            showSource = repositoryUrl == null,
                            onQuickInstall = { quickInstall(module) },
                            quickInstallLoading = resolvingModule == module.catalogId,
                            quickInstallEnabled = resolvingModule == null,
                            onOpen = {
                                viewModel.resolveModule(module.catalogId)?.let {
                                    navigator.push(Route.ModuleRepoDetail(it.moduleId, it.repositoryUrl))
                                }
                            },
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
    showSource: Boolean = true,
    installedVersionCode: Int? = null,
    onQuickInstall: (() -> Unit)? = null,
    quickInstallLoading: Boolean = false,
    quickInstallEnabled: Boolean = true,
) {
    val installed = !module.isScript && (module.installed || installedVersionCode != null)
    val updatable = !module.isScript && installedVersionCode != null && module.latestVersionCode > installedVersionCode
    val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant
    val locale = LocalConfiguration.current.locales[0]
    CatalogCard(onClick = onOpen) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                CatalogCardHeading(
                    title = module.moduleName,
                    leadingContent = {
                        CatalogModuleIcon(
                            url = module.registryMetadata?.iconUrl ?: module.iconUrl,
                            origin = module.registryMetadata?.let { module.repositoryUrl },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                )
                Text(
                    text = stringResource(R.string.repo_module_id_value, module.moduleId),
                    style = MaterialTheme.typography.bodySmall,
                    color = metadataColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (module.authors.isNotBlank()) {
                    Text(
                        text = "${stringResource(R.string.module_author)}: ${module.authors}",
                        style = MaterialTheme.typography.bodySmall,
                        color = metadataColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showSource) {
                    Text(
                        text = stringResource(R.string.repo_source_label, module.repositoryName),
                        style = MaterialTheme.typography.bodySmall,
                        color = metadataColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onQuickInstall != null && (!module.detailLoaded || module.latestAsset?.assets?.isNotEmpty() == true)) {
                key(module.latestAsset?.assets) {
                    QuickInstallButton(module, quickInstallLoading, quickInstallEnabled, onQuickInstall)
                }
            }
        }
        if (module.summary.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = module.summary,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = metadataColor,
                overflow = TextOverflow.Ellipsis,
                maxLines = 4,
            )
        }
        if (module.isScript || module.metamodule || installed) {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (module.isScript) {
                    LabelText(label = stringResource(R.string.module_repo_script_badge), containerColor = MaterialTheme.colorScheme.tertiary)
                }
                if (module.metamodule) {
                    LabelText(
                        label = stringResource(R.string.module_meta_label),
                        containerColor = MaterialTheme.colorScheme.primary,
                    )
                }
                if (installed) {
                    Text(
                        text = stringResource(if (updatable) R.string.repo_update_available else R.string.installed),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (updatable) MaterialTheme.colorScheme.primary else metadataColor,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = Dp.Hairline)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = module.latestRelease,
                    style = MaterialTheme.typography.bodySmall,
                    color = metadataColor,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (module.stargazerCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = Icons.TwoTone.Star,
                            contentDescription = stringResource(R.string.repo_stars),
                            tint = metadataColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(module.stargazerCount.toString(), style = MaterialTheme.typography.bodySmall, color = metadataColor)
                    }
                }
            }
            if (module.latestReleaseTime.isNotBlank()) {
                Text(
                    text = releaseDate(module.latestReleaseTime, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = metadataColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuickInstallButton(module: CatalogModule, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val observeDownload = koinInject<ObserveAssetDownloadUseCase>()
    val assets = module.latestAsset?.assets.orEmpty()
    val activeDownload = remember(assets) {
        if (assets.isEmpty()) {
            flowOf(null)
        } else {
            combine(assets.map { observeDownload(it) }) { downloads ->
                downloads.firstOrNull { it?.status == DownloadStatus.PENDING || it?.status == DownloadStatus.DOWNLOADING }
            }
        }
    }
    val download by activeDownload.collectAsStateWithLifecycle(
        initialValue = assets.mapNotNull { observeDownload.current(it) }
            .firstOrNull { it.status == DownloadStatus.PENDING || it.status == DownloadStatus.DOWNLOADING },
    )
    val downloading = download != null
    val progress = (download?.progress ?: 0).coerceIn(0, 100)
    val description = if (downloading) {
        stringResource(R.string.repo_download_progress, download?.fileName.orEmpty(), progress)
    } else {
        stringResource(if (module.isScript) R.string.script_run else R.string.repo_install_latest)
    }
    IconButton(onClick = onClick, enabled = enabled && !loading && !downloading, modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            downloading -> CircularProgressIndicator(progress = { progress / 100f }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else -> Icon(if (module.isScript) Icons.TwoTone.PlayArrow else Icons.TwoTone.Download, contentDescription = null, modifier = Modifier.size(24.dp))
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
        )
    }
}
