package org.bakasu.bakasu.ui.screen.moduleRepo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.ExpandLess
import androidx.compose.material.icons.twotone.ExpandMore
import androidx.compose.material.icons.twotone.Link
import androidx.compose.material.icons.twotone.OpenInBrowser
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.DocumentFormat
import org.bakasu.bakasu.domain.model.DownloadStatus
import org.bakasu.bakasu.domain.model.ModuleCatalogFailure
import org.bakasu.bakasu.domain.model.ModuleReadme
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.model.ModuleReleaseDetails
import org.bakasu.bakasu.domain.model.documentPolicy
import org.bakasu.bakasu.domain.usecase.GetLastScriptRunUseCase
import org.bakasu.bakasu.domain.usecase.ObserveAssetDownloadUseCase
import org.bakasu.bakasu.ui.activity.PermissionRequestInterface
import org.bakasu.bakasu.ui.component.CatalogCard
import org.bakasu.bakasu.ui.component.GithubMarkdown
import org.bakasu.bakasu.ui.component.HorizontalPagerWithInteraction
import org.bakasu.bakasu.ui.component.SwipeableSnackbarHost
import org.bakasu.bakasu.ui.component.WarningCard
import org.bakasu.bakasu.ui.component.moduleAssetDetails
import org.bakasu.bakasu.ui.component.rememberRepositoryInstallDialog
import org.bakasu.bakasu.ui.component.repositoryErrorText
import org.bakasu.bakasu.ui.component.scriptLastRunText
import org.bakasu.bakasu.ui.component.settings.AppBackButton
import org.bakasu.bakasu.ui.component.settings.SettingsBaseWidget
import org.bakasu.bakasu.ui.component.settings.lazySegmentColumn
import org.bakasu.bakasu.ui.navigation.LocalNavigator
import org.bakasu.bakasu.ui.navigation.Navigator
import org.bakasu.bakasu.ui.navigation.Route
import org.bakasu.bakasu.ui.theme.CardConfig
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.theme.blurEffect
import org.bakasu.bakasu.ui.theme.blurSource
import org.bakasu.bakasu.ui.util.LocalPermissionRequestInterface
import org.bakasu.bakasu.ui.util.LocalSnackbarHost
import org.bakasu.bakasu.ui.util.adaptiveScaffoldWindowInsets
import org.bakasu.bakasu.ui.viewmodel.ModuleDetailUiAction
import org.bakasu.bakasu.ui.viewmodel.ModuleDetailUiState
import org.bakasu.bakasu.ui.viewmodel.ModuleDetailViewModel
import org.bakasu.bakasu.ui.viewmodel.ReleaseDetailsState
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
        val navigator = LocalNavigator.current
        val theme = koinInject<ThemeConfig>()
        val cards = koinInject<CardConfig>()
        val scroll = null ?: TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(
                initialHeightOffset = -154f,
                initialHeightOffsetLimit = -154f, // from debugger
            ),
        )
        val barColor = if (theme.isEnableBlur) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(cards.cardAlpha)
        }
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            topBar = {
                Column(Modifier.blurEffect().background(barColor)) {
                    LargeFlexibleTopAppBar(
                        title = { Text(stringResource(R.string.module_repo), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                        actions = { },
                        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                        scrollBehavior = scroll,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                    )
                }
            },
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            contentWindowInsets = adaptiveScaffoldWindowInsets(),
            snackbarHost = { SwipeableSnackbarHost(hostState = LocalSnackbarHost.current) },
            content = { padding: PaddingValues ->
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    if (state.loading) {
                        LoadingIndicator()
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(state.error?.resId ?: R.string.repo_error_network),
                            )
                            if (state.error != ModuleCatalogFailure.NotFound) {
                                FilledTonalButton(onClick = { viewModel.dispatch(ModuleDetailUiAction.Retry) }) {
                                    Text(stringResource(R.string.network_retry))
                                }
                            }
                        }
                    }
                }
            },
        )
        return
    }
    OnlineModuleDetailContent(module, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnlineModuleDetailContent(module: CatalogModule, viewModel: ModuleDetailViewModel) {
    val coroutineScope = rememberCoroutineScope()

    val tabTitles = listOf(stringResource(R.string.readme), stringResource(R.string.release), stringResource(R.string.info))
    val uriHandler = LocalUriHandler.current
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(
            initialHeightOffset = -154f,
            initialHeightOffsetLimit = -154f, // from debugger
        ),
    )

    BackHandler(pagerState.currentPage != 0) {
        coroutineScope.launch { pagerState.animateScrollToPage(0) }
    }
    val navigator = LocalNavigator.current
    val theme = koinInject<ThemeConfig>()
    val cards = koinInject<CardConfig>()
    val scroll = scrollBehavior ?: TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(
            initialHeightOffset = -154f,
            initialHeightOffsetLimit = -154f, // from debugger
        ),
    )
    val barColor = if (theme.isEnableBlur) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(cards.cardAlpha)
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            Column(Modifier.blurEffect().background(barColor)) {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            text = module.moduleName,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                    actions = {
                        IconButton(onClick = { uriHandler.openUri(module.pageUrl) }) {
                            Icon(
                                imageVector = Icons.TwoTone.OpenInBrowser,
                                contentDescription = stringResource(R.string.open_module_home_page),
                            )
                        }
                    },
                    windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                    scrollBehavior = scroll,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text(title) },
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        snackbarHost = { SwipeableSnackbarHost(hostState = LocalSnackbarHost.current) },
        content = { innerPadding: PaddingValues ->
            HorizontalPagerWithInteraction(
                state = pagerState,
                modifier = Modifier.fillMaxSize().blurSource(),
            ) { page ->
                when (page) {
                    0 -> ReadmeTab(module, scrollBehavior.nestedScrollConnection, innerPadding, viewModel)

                    1 -> ReleasesTab(
                        module,
                        scrollBehavior.nestedScrollConnection,
                        innerPadding,
                        viewModel,
                    )

                    2 -> InfoTab(module, scrollBehavior.nestedScrollConnection, innerPadding, viewModel)
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InfoTab(
    module: CatalogModule,
    nestedScrollConnection: NestedScrollConnection,
    innerPadding: PaddingValues,
    viewModel: ModuleDetailViewModel? = null,
) {
    val state = viewModel?.state?.collectAsStateWithLifecycle()?.value
    val metadata = module.registryMetadata
    val layoutDirection = LocalLayoutDirection.current
    val authorTitle = stringResource(R.string.author)
    val sourceTitle = stringResource(R.string.source_code)
    val infoTitle = stringResource(R.string.info)
    val repositoryTitle = stringResource(R.string.module_repo)
    val info = buildList {
        metadata?.let {
            it.stars?.let { stars ->
                val repository = metadata.starsRepository
                add(ModuleInfoRow(R.string.repo_stars, listOfNotNull(NumberFormat.getIntegerInstance().format(stars), repository).joinToString(" · "), repository?.let { name -> "https://github.com/$name" }))
            }
            it.license?.takeIf(String::isNotBlank)?.let { license -> add(ModuleInfoRow(R.string.repo_license, license)) }
            it.homepage?.takeIf(String::isNotBlank)?.let { url -> add(ModuleInfoRow(R.string.repo_homepage, url, url, allowHttp = true)) }
            it.support?.takeIf(String::isNotBlank)?.let { url -> add(ModuleInfoRow(R.string.repo_support, url, url, allowHttp = true)) }
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 16.dp,
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item(key = "status") { DetailNotice(module, state, viewModel) }
        if (module.isScript) {
            lazySegmentColumn(listOf(module.moduleId), key = { _, id -> "script-history:$id" }) { _, id ->
                val history = koinInject<GetLastScriptRunUseCase>()
                val lastRun = remember(id, history) { history.observe(id) }.collectAsStateWithLifecycle(initialValue = null).value
                SettingsBaseWidget(title = stringResource(R.string.module_repo_script_badge), description = scriptLastRunText(lastRun), iconPlaceholder = false)
            }
            item(key = "script-history-spacing") { Spacer(Modifier.height(8.dp)) }
        }
        if (module.authorList.isNotEmpty()) {
            lazySegmentColumn(module.authorList, title = authorTitle, key = { index, _ -> "author:$index" }) { _, author ->
                ModuleInfoLink(author.name, null, author.link)
            }
            item(key = "author-spacing") { Spacer(Modifier.height(8.dp)) }
        }
        if (module.sourceUrl.isNotBlank()) {
            lazySegmentColumn(listOf(module.sourceUrl), title = sourceTitle, key = { _, _ -> "source" }) { _, url ->
                ModuleInfoLink(
                    metadata?.sourceFullName?.takeIf(String::isNotBlank) ?: url,
                    metadata?.sourceCommit?.takeIf(String::isNotBlank)?.let { stringResource(R.string.repo_source_commit, it.take(12)) },
                    url,
                )
            }
            item(key = "source-spacing") { Spacer(Modifier.height(8.dp)) }
        }
        if (info.isNotEmpty()) {
            lazySegmentColumn(info, title = infoTitle, key = { _, row -> "info:${row.title}" }) { _, row ->
                ModuleInfoLink(stringResource(row.title), row.description, row.url, row.allowHttp)
            }
            item(key = "info-spacing") { Spacer(Modifier.height(8.dp)) }
        }
        lazySegmentColumn(listOf(module.repositoryUrl), title = repositoryTitle, key = { _, _ -> "repository" }) { _, url ->
            ModuleInfoLink(module.repositoryName, url, url)
        }
    }
}

private data class ModuleInfoRow(val title: Int, val description: String, val url: String? = null, val allowHttp: Boolean = false)

@Composable
private fun ModuleInfoLink(title: String, description: String?, url: String?, allowHttp: Boolean = false) {
    val uriHandler = LocalUriHandler.current
    val link = url?.toHttpUrlOrNull()?.takeIf { (it.isHttps || allowHttp) && it.username.isEmpty() && it.password.isEmpty() }?.toString()
    SettingsBaseWidget(
        title = title,
        titleStyle = MaterialTheme.typography.bodyMedium,
        description = description,
        descriptionStyle = MaterialTheme.typography.bodyMedium,
        descriptionColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconPlaceholder = false,
    ) {
        if (link != null) {
            FilledTonalButton(
                modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                onClick = { uriHandler.openUri(link) },
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Icon(
                    Icons.TwoTone.Link,
                    contentDescription = stringResource(R.string.repo_open_link, title),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailNotice(module: CatalogModule, state: ModuleDetailUiState?, viewModel: ModuleDetailViewModel?) {
    val failure = state?.repositoryFailure
    val cached = module.registryMetadata?.fromCache == true || failure != null
    val stale = module.registryMetadata?.healthStale == true
    if (!cached && !stale && state?.error == null) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (cached) Text(stringResource(R.string.repo_using_cache), style = MaterialTheme.typography.bodySmall)
        if (stale) {
            WarningCard(message = stringResource(R.string.repo_health_stale))
        }
        if (failure != null) {
            Text(repositoryErrorText(failure), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else if (state?.error != null) {
            Text(stringResource(R.string.repo_detail_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { viewModel?.dispatch(ModuleDetailUiAction.Retry) }, enabled = state?.loading != true) {
            Text(stringResource(R.string.network_retry))
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
    val state = viewModel?.state?.collectAsStateWithLifecycle()?.value
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 12.dp,
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item(key = "status") { DetailNotice(module, state, viewModel) }
        if (module.detailLoaded && module.releases.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(R.string.repo_no_releases), modifier = Modifier.padding(16.dp))
            }
        }
        module.releases.forEach { release ->
            releaseGroup(module, release, state?.releaseDetails?.get(release), viewModel)
        }
    }
}

private fun LazyListScope.releaseGroup(module: CatalogModule, release: ModuleRelease, state: ReleaseDetailsState?, viewModel: ModuleDetailViewModel?) {
    val releaseKey = "${release.versionCode ?: release.tagName}:${release.assets.firstOrNull()?.downloadUrl.orEmpty()}"
    val details = state?.details
    val releaseUrl = release.releaseUrl.ifBlank { details?.releaseUrl.orEmpty() }
    val rows = buildList<Pair<String, @Composable () -> Unit>> {
        add(
            "heading" to {
                LaunchedEffect(module, release) { viewModel?.loadReleaseDetails(release) }
                ReleaseHeading(release, details)
            },
        )
        if (release.descriptionHTML.isNotBlank() || release.changelogUrl.isNotBlank() || details?.notes != null || details?.notesFailed == true) {
            add(
                "changelog" to {
                    SettingsBaseWidget(
                        title = null,
                        iconPlaceholder = false,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        descriptionColumnContent = { ReleaseNotes(module, release, state, viewModel) },
                    )
                },
            )
        }
        release.assets.forEach { asset ->
            add("asset:${asset.name}:${asset.downloadUrl}" to { ReleaseAsset(module, release, asset, viewModel) })
        }
        if (releaseUrl.isNotBlank()) {
            add(
                "release" to {
                    ModuleInfoLink(
                        stringResource(R.string.repo_release_page),
                        release.tagCommit.takeIf(String::isNotBlank)?.let { stringResource(R.string.repo_source_commit, it.take(12)) },
                        releaseUrl,
                    )
                },
            )
        }
    }
    lazySegmentColumn(rows, key = { _, row -> "$releaseKey:${row.first}" }) { _, row -> row.second() }
    item(key = "$releaseKey:spacing") { Spacer(Modifier.height(12.dp)) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReadmeTab(
    module: CatalogModule,
    nestedScrollConnection: NestedScrollConnection,
    innerPadding: PaddingValues,
    viewModel: ModuleDetailViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val layoutDirection = LocalLayoutDirection.current
    val documentUrl = module.readmeUrl.ifBlank { module.sourceUrl }
    val document = state.documents[documentUrl]
    LaunchedEffect(module.catalogId, documentUrl, module.detailLoaded, module.registryMetadata?.buildId) {
        if (module.detailLoaded) viewModel.loadReadme()
    }
    val readme = document?.document
    val content = readme?.content.orEmpty()
    val htmlLoading = remember(content) { mutableStateOf(true) }
    val detailFailed = !module.detailLoaded && state.error != null
    val documentLoading = !detailFailed && (!module.detailLoaded || document == null || document.loading)
    val documentFailed = detailFailed || document?.failed == true
    Box(
        Modifier.fillMaxSize().padding(
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
        ),
    ) {
        LazyColumn(
            Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item(key = "status") { DetailNotice(module, state, viewModel) }
            when {
                documentLoading -> Unit

                documentFailed -> item(key = "error") {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.repo_document_error))
                        TextButton(onClick = {
                            if (!module.detailLoaded) viewModel.dispatch(ModuleDetailUiAction.Retry) else viewModel.loadReadme(retry = true)
                        }) {
                            Text(stringResource(R.string.network_retry))
                        }
                    }
                }

                content.isBlank() -> item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.repo_no_readme))
                    }
                }

                else -> item(key = "document") {
                    CatalogCard(
                        modifier = Modifier.padding(16.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        GithubMarkdown(
                            document = requireNotNull(readme),
                            backgroundColor = Color.Transparent,
                            loading = htmlLoading,
                            readmeLinks = true,
                            callerProvideLoadingIndicator = true,
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

@Composable
private fun ReleaseHeading(release: ModuleRelease, details: ModuleReleaseDetails?) {
    val title = details?.title ?: release.name
    val publishedAt = release.publishedAt.ifBlank { details?.publishedAt.orEmpty() }
    val metadata = listOfNotNull(
        release.tagName.takeIf { it.isNotBlank() && it != title },
        release.versionCode?.toString(),
    ).joinToString(" · ")
    SettingsBaseWidget(
        title = title,
        titleStyle = MaterialTheme.typography.bodyLarge,
        description = metadata.takeIf(String::isNotBlank),
        descriptionStyle = MaterialTheme.typography.bodyMedium,
        descriptionColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconPlaceholder = false,
    ) {
        if (publishedAt.isNotBlank()) {
            Text(
                releaseDate(publishedAt, LocalConfiguration.current.locales[0]),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReleaseAsset(module: CatalogModule, release: ModuleRelease, asset: ModuleReleaseAsset, viewModel: ModuleDetailViewModel?) {
    val confirmDownload = rememberRepositoryInstallDialog()
    val observeDownload = koinInject<ObserveAssetDownloadUseCase>()
    val downloadFlow = remember(asset) { observeDownload(asset) }
    val download by downloadFlow.collectAsStateWithLifecycle(initialValue = observeDownload.current(asset))
    val downloading = download?.status == DownloadStatus.PENDING || download?.status == DownloadStatus.DOWNLOADING
    val progress = (download?.progress ?: 0).coerceIn(0, 100)
    val downloadDescription = if (downloading) {
        stringResource(R.string.repo_download_progress, asset.name, progress)
    } else if (module.isScript) {
        stringResource(R.string.script_run)
    } else {
        stringResource(R.string.repo_download_asset, asset.name)
    }
    val onClick = {
        if (!downloading) {
            viewModel?.resolveAsset(asset)?.let { current ->
                confirmDownload(current, asset) { viewModel.resolveAsset(asset) != null }
            }
        }
    }
    SettingsBaseWidget(
        title = asset.name,
        titleStyle = MaterialTheme.typography.bodyLarge,
        iconPlaceholder = false,
        description = moduleAssetDetails(asset),
        descriptionStyle = MaterialTheme.typography.bodyMedium,
        descriptionColor = MaterialTheme.colorScheme.onSurfaceVariant,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        descriptionColumnContent = {
            asset.expected?.let { expected ->
                val fullHash = stringResource(R.string.repo_sha256, expected.sha256)
                Text(
                    text = stringResource(R.string.repo_sha256, expected.sha256.take(12) + "…" + expected.sha256.takeLast(8)),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = fullHash },
                )
            }
            if (release.immutable) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Verified, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.repo_immutable_release), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    ) {
        FilledTonalButton(
            modifier = Modifier.semantics { contentDescription = downloadDescription },
            onClick = onClick,
            enabled = !downloading,
            contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) {
            if (downloading) {
                CircularProgressIndicator(progress = { progress / 100f }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(if (module.isScript) Icons.TwoTone.PlayArrow else Icons.TwoTone.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (module.isScript) R.string.script_run else R.string.repo_download), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

internal fun releaseDate(value: String, locale: Locale): String = runCatching {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .format(Instant.parse(value).atZone(ZoneId.systemDefault()))
}.getOrDefault(value)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReleaseNotes(module: CatalogModule, release: ModuleRelease, state: ReleaseDetailsState?, viewModel: ModuleDetailViewModel?) {
    LaunchedEffect(module, release) { viewModel?.loadReleaseDetails(release) }
    val policy = module.documentPolicy()
    val document = state?.details?.notes ?: release.descriptionHTML.takeIf(String::isNotBlank)?.let {
        ModuleReadme(it, module.repositoryUrl, DocumentFormat.HTML, policy)
    }
    var expanded by rememberSaveable(module.catalogId, release.tagName, release.versionCode) { mutableStateOf(false) }
    val htmlLoading = remember(document) { mutableStateOf(true) }
    val documentLoading = document == null && release.changelogUrl.isNotBlank() && (state == null || state.loading)
    val documentFailed = state?.details?.notesFailed == true
    Column(Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.show_detail_or_hide_detail), modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.TwoTone.ExpandLess else Icons.TwoTone.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.repo_collapse_changelog else R.string.repo_expand_changelog),
            )
        }
        if (expanded) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    documentLoading -> LoadingIndicator()

                    documentFailed -> Column {
                        Text(stringResource(R.string.repo_changelog_error))
                        TextButton(onClick = { viewModel?.loadReleaseDetails(release, retry = true) }) {
                            Text(stringResource(R.string.network_retry))
                        }
                    }

                    document != null -> {
                        GithubMarkdown(
                            document = document,
                            backgroundColor = Color.Transparent,
                            loading = htmlLoading,
                            callerProvideLoadingIndicator = true,
                        )
                        if (htmlLoading.value) LoadingIndicator()
                    }
                }
            }
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
        LazyColumn { releaseGroup(fakeModule, release, state = null, viewModel = null) }
    }
}
