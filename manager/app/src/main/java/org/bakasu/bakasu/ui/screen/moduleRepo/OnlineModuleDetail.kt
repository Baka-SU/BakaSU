package org.bakasu.bakasu.ui.screen.moduleRepo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Code
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.KeyboardArrowDown
import androidx.compose.material.icons.twotone.Link
import androidx.compose.material.icons.twotone.OpenInBrowser
import androidx.compose.material.icons.twotone.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.ModuleCatalogFailure
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.model.RepositorySource
import org.bakasu.bakasu.ui.activity.PermissionRequestInterface
import org.bakasu.bakasu.ui.component.CatalogCard
import org.bakasu.bakasu.ui.component.GithubMarkdown
import org.bakasu.bakasu.ui.component.HorizontalPagerWithInteraction
import org.bakasu.bakasu.ui.component.RepositoryPageScaffold
import org.bakasu.bakasu.ui.component.SwipeableSnackbarHost
import org.bakasu.bakasu.ui.component.moduleAssetDetails
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.component.rememberRepositoryInstallDialog
import org.bakasu.bakasu.ui.component.rememberRepositoryScrollBehavior
import org.bakasu.bakasu.ui.component.settings.AppBackButton
import org.bakasu.bakasu.ui.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.component.settings.SettingsBaseWidget
import org.bakasu.bakasu.ui.component.settings.lazySegmentColumn
import org.bakasu.bakasu.ui.navigation.LocalNavigator
import org.bakasu.bakasu.ui.navigation.Navigator
import org.bakasu.bakasu.ui.navigation.Route
import org.bakasu.bakasu.ui.theme.CardConfig
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.theme.blurEffect
import org.bakasu.bakasu.ui.theme.blurSource
import org.bakasu.bakasu.ui.theme.renderBackgroundBlur
import org.bakasu.bakasu.ui.util.LocalPermissionRequestInterface
import org.bakasu.bakasu.ui.util.LocalSnackbarHost
import org.bakasu.bakasu.ui.util.adaptiveScaffoldWindowInsets
import org.bakasu.bakasu.ui.viewmodel.ModuleDetailUiAction
import org.bakasu.bakasu.ui.viewmodel.ModuleDetailViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * @author AlexLiuDev233
 * @date 2025/12/7
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlineModuleDetailScreen(
    moduleId: String,
    repositoryUrl: String,
) {
    val viewModel = koinViewModel<ModuleDetailViewModel>(
        key = "$repositoryUrl#$moduleId",
        parameters = { parametersOf(moduleId, repositoryUrl) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val module = state.module
    if (module == null) {
        RepositoryPageScaffold(stringResource(R.string.module_repo)) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.loading) {
                    LoadingIndicator()
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(
                                when (state.error) {
                                    ModuleCatalogFailure.Offline -> R.string.network_offline
                                    ModuleCatalogFailure.NotFound -> R.string.repo_module_unavailable
                                    else -> R.string.repo_error_network
                                },
                            ),
                        )
                        if (state.error != ModuleCatalogFailure.NotFound) {
                            FilledTonalButton(onClick = { viewModel.dispatch(ModuleDetailUiAction.Retry) }) {
                                Text(stringResource(R.string.network_retry))
                            }
                        }
                    }
                }
            }
        }
        return
    }
    OnlineModuleDetailContent(module, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnlineModuleDetailContent(module: CatalogModule, viewModel: ModuleDetailViewModel) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val coroutineScope = rememberCoroutineScope()

    val tabTitles = listOf(stringResource(R.string.readme), stringResource(R.string.release), stringResource(R.string.info))
    val uriHandler = LocalUriHandler.current
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scrollBehavior = rememberRepositoryScrollBehavior(pagerState.currentPage)
    val confirmDownload = rememberRepositoryInstallDialog()
    fun downloadLatest(asset: ModuleReleaseAsset) {
        val current = viewModel.resolveAsset(asset) ?: return
        if (current.latestAsset?.assets?.any { it.hasSameIdentity(asset) } != true) return
        confirmDownload(current, asset) { viewModel.resolveAsset(asset)?.latestAsset?.assets?.any { it.hasSameIdentity(asset) } == true }
    }
    val chooseDialog = rememberCustomDialog { dismiss ->
        ChooseDialogContent(module, onSelect = { downloadLatest(it) }, dismiss = dismiss)
    }
    val installLatest = {
        val assets = module.latestAsset?.assets.orEmpty()
        if (assets.size == 1) {
            downloadLatest(assets.single())
        } else if (assets.size > 1) {
            chooseDialog.show()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.blurEffect(),
            ) {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(module.moduleName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        AppBackButton(
                            onClick = {
                                navigator.pop()
                            },
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                uriHandler.openUri(module.pageUrl)
                            },
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.OpenInBrowser,
                                contentDescription = stringResource(R.string.open_module_home_page),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors().copy(
                        containerColor =
                            if (themeConfig.isEnableBlur) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                            },
                        scrolledContainerColor =
                            if (themeConfig.isEnableBlur) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                            },
                    ),
                    windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                )

                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor =
                        if (themeConfig.isEnableBlur) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                        },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text(title) },
                        )
                    }
                }

                BackHandler(
                    pagerState.currentPage != 0,
                ) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                }
            }
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        floatingActionButton = {
            if (pagerState.currentPage != 1 && module.latestAsset?.assets?.isNotEmpty() == true) {
                ExtendedFloatingActionButton(
                    onClick = installLatest,
                    icon = { Icon(Icons.TwoTone.Download, contentDescription = null) },
                    text = { Text(stringResource(R.string.repo_download_latest)) },
                )
            }
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurSource(),
        ) {
            HorizontalPagerWithInteraction(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 2,
            ) { page ->
                when (page) {
                    0 -> ReadmeTab(module, scrollBehavior.nestedScrollConnection, innerPadding, viewModel)

                    1 -> ReleasesTab(
                        module,
                        scrollBehavior.nestedScrollConnection,
                        innerPadding,
                        viewModel,
                    )

                    2 -> InfoTab(module, scrollBehavior.nestedScrollConnection, innerPadding)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InfoTab(
    module: CatalogModule,
    nestedScrollConnection: NestedScrollConnection,
    innerPadding: PaddingValues,
) {
    val uriHandler = LocalUriHandler.current
    val authorTitle = stringResource(R.string.author)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
            .nestedScroll(nestedScrollConnection),
    ) {
        item {
            Spacer(Modifier.height(innerPadding.calculateTopPadding()))
        }
        item {
            SegmentedColumn(title = stringResource(R.string.info)) {
                item {
                    SettingsBaseWidget(
                        title = module.moduleName,
                        description = module.moduleId,
                        iconPlaceholder = false,
                    )
                }
            }
        }
        lazySegmentColumn(module.authorList, title = authorTitle) { _, author ->
            SettingsBaseWidget(
                icon = Icons.TwoTone.Person,
                title = author.name,
                onClick = if (author.link.isNotBlank()) ({ uriHandler.openUri(author.link) }) else null,
            ) {
                if (author.link.isNotBlank()) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = Icons.TwoTone.Link,
                        contentDescription = stringResource(R.string.author_link),
                    )
                }
            }
        }

        if (module.sourceUrl.isNotBlank()) {
            item {
                SegmentedColumn(
                    title = stringResource(R.string.source_code),
                ) {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Code,
                            title = module.sourceUrl,
                            onClick = {
                                uriHandler.openUri(module.sourceUrl)
                            },
                        )
                    }
                }
            }
        }

        item {
            SegmentedColumn(title = stringResource(R.string.module_repo)) {
                item {
                    SettingsBaseWidget(
                        title = module.repositoryName,
                        description = module.repositoryUrl,
                        iconPlaceholder = false,
                        onClick = { uriHandler.openUri(module.repositoryUrl) },
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 88.dp))
        }
    }
}

@Composable
fun ReleasesTab(
    module: CatalogModule,
    nestedScrollConnection: NestedScrollConnection,
    innerPadding: PaddingValues,
    viewModel: ModuleDetailViewModel? = null,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            // Info adds 16.dp outside the list and 16.dp before its section title.
            Spacer(Modifier.height(innerPadding.calculateTopPadding() + 24.dp))
        }
        items(
            items = module.releases,
            key = { "${it.versionCode ?: it.tagName}:${it.assets.firstOrNull()?.downloadUrl.orEmpty()}" },
        ) {
            ReleaseCard(module, it, viewModel)
        }
        item {
            Spacer(Modifier.height(innerPadding.calculateBottomPadding() + 88.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReadmeTab(
    module: CatalogModule,
    nestedScrollConnection: NestedScrollConnection,
    innerPadding: PaddingValues,
    viewModel: ModuleDetailViewModel? = null,
) {
    val state = viewModel?.state?.collectAsStateWithLifecycle()?.value
    val remote = module.repositoryUrl != RepositorySource.KERNEL_SU_URL
    val documentUrl = module.readmeUrl.ifBlank { module.sourceUrl }
    val document = state?.documents?.get(documentUrl)
    LaunchedEffect(module.catalogId, documentUrl) { if (remote) viewModel?.loadReadme() }
    val content = (if (remote) document?.text else module.readme).orEmpty()
    val htmlLoading = remember(content) { mutableStateOf(true) }
    val documentLoading = remote && (document == null || document.loading)
    val documentFailed = remote && document?.failed == true
    Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
        when {
            documentLoading -> Unit

            documentFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.repo_document_error))
                TextButton(onClick = { viewModel?.loadReadme(retry = true) }) {
                    Text(stringResource(R.string.network_retry))
                }
            }

            content.isBlank() -> Text(stringResource(R.string.repo_no_readme))

            else -> {
                LazyColumn(
                    Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                ) {
                    item {
                        GithubMarkdown(
                            content = content,
                            backgroundColor = Color.Transparent,
                            loading = htmlLoading,
                            callerProvideLoadingIndicator = true,
                            renderMarkdown = remote,
                            baseUrl = if (remote) document?.baseUrl.orEmpty() else "https://appassets.androidplatform.net",
                        )
                    }
                }
            }
        }
        if (documentLoading || (!documentFailed && content.isNotBlank() && htmlLoading.value)) {
            LoadingIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReleaseCard(
    module: CatalogModule,
    release: ModuleRelease,
    viewModel: ModuleDetailViewModel? = null,
) {
    val confirmDownload = rememberRepositoryInstallDialog()
    val isLatest = release == module.latestAsset
    val hasReleaseNotes = release.descriptionHTML.isNotBlank() || release.changelogUrl.isNotBlank()

    CatalogCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        CollapsibleContent(
            enabled = hasReleaseNotes,
            enter = EnterTransition.None,
            title = {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = release.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isLatest) {
                        Text(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            text = stringResource(R.string.repo_latest_release),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = releaseDate(release.publishedAt, LocalConfiguration.current.locales[0]),
                    style = MaterialTheme.typography.bodySmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        ) {
            if (hasReleaseNotes) ReleaseNotes(release, viewModel)
        }
        if (release.assets.isEmpty()) return@CatalogCard
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        Column {
            release.assets.forEach { assetInfo ->
                val downloadDescription = stringResource(R.string.repo_download_asset, assetInfo.name)
                val onClick: () -> Unit = {
                    val current = viewModel?.resolveAsset(assetInfo)
                    if (current != null) {
                        confirmDownload(current, assetInfo) { viewModel.resolveAsset(assetInfo) != null }
                    }
                }
                SettingsBaseWidget(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .renderBackgroundBlur(tintColor = MaterialTheme.colorScheme.surfaceBright),
                    title = assetInfo.name,
                    onClick = { onClick() },
                    iconPlaceholder = false,
                    description = moduleAssetDetails(assetInfo),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    isOnBackground = false,
                    containerColor = Color.Transparent,
                ) {
                    FilledTonalButton(
                        modifier = Modifier
                            .heightIn(min = if (isLatest) 48.dp else 40.dp)
                            .semantics { contentDescription = downloadDescription },
                        onClick = onClick,
                        contentPadding = if (isLatest) ButtonDefaults.ContentPadding else ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.TwoTone.Download,
                            contentDescription = null,
                        )
                        if (isLatest) {
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.repo_download))
                        }
                    }
                }
            }
        }
    }
}

private fun releaseDate(value: String, locale: Locale): String = runCatching {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .format(Instant.parse(value).atZone(ZoneId.systemDefault()))
}.getOrDefault(value)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReleaseNotes(release: ModuleRelease, viewModel: ModuleDetailViewModel?) {
    val remote = release.changelogUrl.isNotBlank()
    val state = viewModel?.state?.collectAsStateWithLifecycle()?.value
    val document = state?.documents?.get(release.changelogUrl)
    LaunchedEffect(release.changelogUrl) { if (remote) viewModel?.loadChangelog(release.changelogUrl) }
    val content = if (remote) document?.text.orEmpty() else release.descriptionHTML
    val htmlLoading = remember(content) { mutableStateOf(true) }
    val documentLoading = remote && (document == null || document.loading)
    val documentFailed = remote && document?.failed == true
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            documentLoading -> Unit

            documentFailed -> Column {
                Text(stringResource(R.string.repo_changelog_error))
                TextButton(onClick = { viewModel?.loadChangelog(release.changelogUrl, retry = true) }) {
                    Text(stringResource(R.string.network_retry))
                }
            }

            else -> GithubMarkdown(
                content = content,
                backgroundColor = Color.Transparent,
                loading = htmlLoading,
                callerProvideLoadingIndicator = true,
                renderMarkdown = remote,
                baseUrl = if (remote) document?.baseUrl.orEmpty() else "https://appassets.androidplatform.net",
            )
        }
        if (documentLoading || (!documentFailed && htmlLoading.value)) LoadingIndicator()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CollapsibleContent(
    modifier: Modifier = Modifier,
    title: @Composable RowScope.() -> Unit,
    enabled: Boolean,
    enter: EnterTransition = expandVertically() + fadeIn(),
    exit: ExitTransition = shrinkVertically() + fadeOut(),
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)
    val changelogLabel = stringResource(R.string.module_changelog)

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (enabled) {
                        Modifier.clickable(role = Role.Button, onClickLabel = changelogLabel) { expanded = !expanded }
                    } else {
                        Modifier
                    },
                )
                .heightIn(min = 48.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            title()
            if (enabled) {
                Icon(
                    imageVector = Icons.TwoTone.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && enabled,
            enter = enter,
            exit = exit,
        ) {
            content()
        }
    }
}

@Composable
@Preview
private fun ReleaseCardPreview() {
    val release = ModuleRelease(
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
    )

    val fakeModule = initFakeRepoModuleForPreview()

    CompositionLocalProvider(
        LocalNavigator provides Navigator(Route.ModuleRepoDetail(fakeModule.moduleId, fakeModule.repositoryUrl)),
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
        ReleaseCard(fakeModule, release)
    }
}
