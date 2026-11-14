package org.bakasu.bakasu.ui.wear

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.CompactButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.drawCircularProgressIndicator
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.DocumentFormat
import org.bakasu.bakasu.domain.model.DownloadFailureReason
import org.bakasu.bakasu.domain.model.DownloadModuleInfo
import org.bakasu.bakasu.domain.model.DownloadVerificationFailure
import org.bakasu.bakasu.domain.model.ModuleCatalogFailure
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.viewmodel.CatalogContent
import org.bakasu.bakasu.ui.viewmodel.ModuleDetailUiAction
import org.bakasu.bakasu.ui.viewmodel.ModuleDetailViewModel
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadPhase
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadUiState
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadViewModel
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoUiAction
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoUiState
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoViewModel
import org.bakasu.bakasu.ui.viewmodel.formatFileSize
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearBadge
import org.bakasu.bakasu.ui.wear.component.WearCatalogModuleIcon
import org.bakasu.bakasu.ui.wear.component.WearDialogIcon
import org.bakasu.bakasu.ui.wear.component.WearDialogVerticalArrangement
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearIconAction
import org.bakasu.bakasu.ui.wear.component.WearIconButtonGroup
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearLoadFailureContent
import org.bakasu.bakasu.ui.wear.component.WearLoadFailureScreen
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearScaledItem
import org.bakasu.bakasu.ui.wear.component.WearSearchPage
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearTimeText
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.bakasu.bakasu.ui.wear.component.wearDialogContentPadding
import org.bakasu.bakasu.ui.wear.component.wearRepositoryFailureText
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun WearModuleRepoPage(
    viewModel: ModuleRepoViewModel,
    onBack: () -> Unit,
    onOpenPanel: () -> Unit,
    hasOperationsPanel: Boolean = false,
    isPageLoading: Boolean = false,
    onOpen: (CatalogModule) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var retryLoading by remember(viewModel) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    WearModuleRepoPage(
        state,
        { viewModel.refresh(fromPull = true) },
        onBack,
        onOpenPanel,
        hasOperationsPanel,
        isPageLoading = isPageLoading || retryLoading,
        onRetry = {
            retryLoading = true
            scope.launch {
                try {
                    viewModel.refresh().join()
                } finally {
                    retryLoading = false
                }
            }
        },
        onOpen = onOpen,
    )
}

@Composable
internal fun WearModuleRepoPage(
    state: ModuleRepoUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenPanel: () -> Unit,
    hasOperationsPanel: Boolean = false,
    isPageLoading: Boolean = false,
    onRetry: () -> Unit = onRefresh,
    onOpen: (CatalogModule) -> Unit,
) {
    val loading = isPageLoading || (state.isRefreshing && !state.isPullRefreshing)
    val failed = !loading && !state.isRefreshing && state.content == CatalogContent.FAILED
    WearList(
        pageTitle = stringResource(R.string.module_repo),
        isLoading = loading,
        isRefreshing = state.isRefreshing && state.isPullRefreshing && !loading,
        onRefresh = onRefresh,
        onBack = onBack,
        onConfirm = if (failed) onRetry else null,
        confirmLabel = if (failed) stringResource(R.string.network_retry) else null,
        backToTop = !failed,
        onOpenPanel = onOpenPanel,
        hasOperationsPanel = hasOperationsPanel,
        panelLabel = stringResource(R.string.wear_operations),
    ) { spec ->
        if (failed) {
            item {
                WearLoadFailureContent(spec, wearRepositoryFailureText(state.failures[state.repositoryUrl]))
            }
        } else if (state.modules.isEmpty() && !state.isRefreshing) {
            item {
                WearEmptyState(spec, Icons.Rounded.Cloud, stringResource(state.content.resId ?: R.string.wear_catalog_empty))
            }
        }
        LazySegmentedColumn(if (failed) emptyList() else state.modules, { it.catalogId }) { module ->
            WearSettingsJumpPageWidget(
                spec,
                module.moduleName,
                { onOpen(module) },
                iconContent = {
                    val iconSize = with(LocalDensity.current) {
                        MaterialTheme.typography.labelMedium.lineHeight.toDp() + MaterialTheme.typography.labelSmall.lineHeight.toDp()
                    }.coerceAtLeast(ButtonDefaults.LargeIconSize)
                    WearCatalogModuleIcon(module, iconSize)
                },
                largeIcon = true,

                description = module.summary.ifBlank { module.moduleId },
                titleBadge = if (module.metamodule) stringResource(R.string.module_meta_label) else null,
            )
        }
    }
}

/** The repository search input, a route of its own; it searches the list it was opened from. */
@Composable
internal fun WearModuleRepoSearchPage(viewModel: ModuleRepoViewModel, onBack: () -> Unit, onSubmit: () -> Unit, onAdvanced: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WearSearchPage(stringResource(R.string.search_modules), "repository:${state.repositoryUrl}", state.search, onBack, onAdvanced) {
        viewModel.dispatch(ModuleRepoUiAction.Search(it.trim()))
        onSubmit()
    }
}

/** Each detail route loads from the server; closing it releases its document state. */
@Composable
private fun catalogModuleViewModel(moduleId: String, repositoryUrl: String): ModuleDetailViewModel {
    val viewModel = koinViewModel<ModuleDetailViewModel>(
        key = "wear-catalog-$repositoryUrl#$moduleId",
        parameters = { parametersOf(moduleId, repositoryUrl) },
    )
    return viewModel
}

/** The download confirmation; confirming opens the download route for the chosen file. */
@Composable
private fun rememberModuleDownload(module: CatalogModule?, installer: WearModuleInstaller): (ModuleRelease, ModuleReleaseAsset) -> Unit {
    var pendingRelease by remember { mutableStateOf<ModuleRelease?>(null) }
    var pendingAsset by remember { mutableStateOf<ModuleReleaseAsset?>(null) }
    val thenInstall = stringResource(R.string.wear_download_then_install)
    val confirmDialog = rememberCustomDialog { dismiss ->
        val asset = pendingAsset
        AlertDialog(
            verticalArrangement = WearDialogVerticalArrangement,
            contentPadding = wearDialogContentPadding(),
            visible = true,
            onDismissRequest = dismiss,
            icon = { WearDialogIcon(Icons.Rounded.Download) },
            title = { Text(stringResource(R.string.wear_download_confirm_title)) },
            text = {
                if (asset != null) {
                    Text(stringResource(R.string.wear_catalog_summary, asset.name, formatFileSize(asset.size ?: 0L)) + "\n" + thenInstall)
                }
            },
            confirmButton = {
                AlertDialogDefaults.ConfirmButton(
                    onClick = {
                        dismiss()
                        val release = pendingRelease
                        if (module != null && release != null && asset != null) installer.download(module, release, asset)
                    },
                )
            },
            dismissButton = { AlertDialogDefaults.DismissButton(onClick = dismiss) },
        )
    }
    return { release, asset ->
        pendingRelease = release
        pendingAsset = asset
        confirmDialog.show()
    }
}

@Composable
internal fun WearOnlineModulePage(
    moduleId: String,
    repositoryUrl: String,
    onBack: () -> Unit,
    onLink: (String) -> Unit,
    onSection: (String) -> Unit,
    installer: WearModuleInstaller,
) {
    val viewModel = catalogModuleViewModel(moduleId, repositoryUrl)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val module = state.module
    val requestDownload = rememberModuleDownload(module, installer)
    if (!state.loading && (state.error != null || state.repositoryFailure != null)) {
        WearLoadFailureScreen(
            if (state.error == ModuleCatalogFailure.NotFound) stringResource(R.string.repo_module_unavailable) else wearRepositoryFailureText(state.repositoryFailure),
            onBack,
            { viewModel.dispatch(ModuleDetailUiAction.Retry) },
            retryInEdgeButton = true,
        )
        return
    }
    WearList(isLoading = state.loading, onBack = onBack, backToTop = true) { spec ->
        if (module == null) item { WearPageHeader(spec, stringResource(R.string.module_repo)) }
        if (module != null) {
            // As in the design: the module icon, name and authors, then its main actions. As the
            // first item the icon takes the card's minimum list padding, a share of the screen
            // height, so it stays below the time text on any screen size.
            item {
                WearScaledItem(spec, Modifier.fillMaxWidth().minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        WearCatalogModuleIcon(module, 52.dp)
                        // The bold name leads; authors and version follow in smaller, neutral styles.
                        Text(
                            module.moduleName,
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (module.metamodule) {
                            WearBadge(stringResource(R.string.module_meta_label), Modifier.padding(top = 4.dp))
                        }
                        if (module.authors.isNotBlank()) {
                            Text(
                                module.authors,
                                modifier = Modifier.padding(top = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (module.latestRelease.isNotBlank()) {
                            Text(
                                module.latestRelease,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            val latestRelease = module.releases.firstOrNull { it.assets.isNotEmpty() }
            val latest = latestRelease?.assets?.firstOrNull()
            item {
                WearIconButtonGroup(
                    spec,
                    listOfNotNull(
                        WearIconAction(Icons.Rounded.Download, stringResource(R.string.wear_download), { if (latestRelease != null && latest != null) requestDownload(latestRelease, latest) }, latest != null, primary = true),
                        module.sourceUrl.takeIf { it.isNotBlank() }?.let { url ->
                            WearIconAction(Icons.Rounded.Code, stringResource(R.string.source_code), { onLink(url) })
                        },
                    ),
                )
            }
            item { WearSectionHeader(spec, stringResource(R.string.wear_module_data)) }
            val sections = listOf(
                Triple("readme", R.string.readme, R.string.wear_readme_summary) to Icons.Rounded.Article,
                Triple("releases", R.string.release, R.string.wear_release_summary) to Icons.Rounded.Tag,
                Triple("info", R.string.info, R.string.wear_info_summary) to Icons.Rounded.Info,
            )
            SegmentedColumn(sections, { it.first.first }) { (section, icon) ->
                val (target, label, summary) = section
                WearSettingsJumpPageWidget(spec, stringResource(label), { onSection(target) }, icon = icon, description = stringResource(summary))
            }
            item { WearSectionHeader(spec, stringResource(R.string.wear_authors_source)) }
            LazySegmentedColumn(module.authorList.filter { it.link.isNotBlank() }, { it.link }) { author ->
                WearSettingsJumpPageWidget(
                    spec,
                    author.name,
                    { onLink(author.link) },
                    icon = Icons.Rounded.Person,
                    description = stringResource(R.string.wear_author_link_summary),
                )
            }
        }
    }
}

/** The README, releases or information of a repository module, each opened as its own route. */
@Composable
internal fun WearOnlineModuleSectionPage(moduleId: String, repositoryUrl: String, section: String, onBack: () -> Unit, installer: WearModuleInstaller) {
    val viewModel = catalogModuleViewModel(moduleId, repositoryUrl)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val module = state.module
    val requestDownload = rememberModuleDownload(module, installer)
    // The README is its own document, loaded once the module details are.
    val readme = module?.let { state.documents[it.readmeUrl.ifBlank { it.sourceUrl }] }
    LaunchedEffect(module?.catalogId, module?.detailLoaded, module?.registryMetadata?.buildId, state.loading) {
        if (section == "readme" && !state.loading && module?.detailLoaded == true) viewModel.loadReadme()
    }
    val readmeLoading = section == "readme" && module != null && (!module.detailLoaded || readme == null || readme.loading) && state.error == null
    if (!state.loading && (state.error != null || state.repositoryFailure != null || (section == "readme" && readme?.failed == true))) {
        val detailFailed = state.error != null || state.repositoryFailure != null
        WearLoadFailureScreen(
            if (state.error == ModuleCatalogFailure.NotFound) stringResource(R.string.repo_module_unavailable) else wearRepositoryFailureText(if (detailFailed) state.repositoryFailure else readme?.failure),
            onBack,
            { if (detailFailed) viewModel.dispatch(ModuleDetailUiAction.Retry) else viewModel.loadReadme(retry = true) },
            retryInEdgeButton = true,
        )
        return
    }
    val loading = state.loading || readmeLoading
    val hasContent = module != null && (section != "readme" || readme?.document != null)
    WearList(pageTitle = module?.moduleName ?: stringResource(R.string.module_repo), isLoading = loading && !hasContent, isRefreshing = loading && hasContent, onBack = onBack) { spec ->
        if (module != null) {
            when (section) {
                "readme" -> when {
                    readmeLoading && readme?.document == null -> Unit

                    else -> {
                        val document = readme?.document
                        val text = when {
                            document == null || document.content.isBlank() -> null
                            document.format == DocumentFormat.HTML -> HtmlCompat.fromHtml(document.content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                            else -> document.content.trim()
                        }
                        // The whole README reads as one card, like a license text.
                        item {
                            WearInfoCard(spec) {
                                Text(
                                    text ?: stringResource(R.string.repo_no_readme),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                "releases" -> {
                    // Each release keeps its own files, so an earlier version can still be downloaded.
                    module.releases.forEach { release ->
                        item { WearSectionHeader(spec, release.name.ifBlank { release.tagName }) }
                        item {
                            WearInfoCard(spec) {
                                Text(release.publishedAt, style = MaterialTheme.typography.labelSmall)
                                Text(HtmlCompat.fromHtml(release.descriptionHTML, HtmlCompat.FROM_HTML_MODE_COMPACT).toString(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        items(release.assets, key = { it.downloadUrl }) { asset ->
                            WearActionButton(
                                spec,
                                Icons.Rounded.Download,
                                asset.name,
                                { requestDownload(release, asset) },
                                secondaryText = asset.size?.let(::formatFileSize),
                            )
                        }
                    }
                }

                else -> item {
                    WearInfoCard(spec) {
                        Text(module.moduleId, style = MaterialTheme.typography.titleSmall)
                        Text(module.summary, style = MaterialTheme.typography.bodySmall)
                        Text(module.authors, style = MaterialTheme.typography.bodySmall)
                        Text(module.latestRelease, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/**
 * One service-owned module download, observed while this route is open. Closing the route keeps
 * the transfer running; a verified file completed here goes to [onDownloaded].
 */
@Composable
internal fun WearModuleDownloadPage(route: WearRoute.OnlineModuleDownload, onDownloaded: (String) -> Unit) {
    val viewModel = koinViewModel<ModuleDownloadViewModel>()
    val downloadState = viewModel.state.collectAsStateWithLifecycle()
    val download by downloadState
    val asset = remember(route) { route.asset() }
    LaunchedEffect(viewModel) {
        if (viewModel.state.value.phase == null) viewModel.start(asset, DownloadModuleInfo(route.name, route.iconUrl, route.iconOrigin, route.moduleId))
    }
    LaunchedEffect(download.resultUri) {
        download.resultUri?.let(onDownloaded)
    }
    WearModuleDownloadScreen(
        route.name,
        download,
        // Read through the state when the ring draws, not captured once.
        fraction = { downloadState.value.fraction ?: 1f.takeIf { downloadState.value.phase == ModuleDownloadPhase.VERIFYING } ?: 0f },
        iconUrl = route.iconUrl,
        iconOrigin = route.iconOrigin,
    )
}

/**
 * Download identity stays fixed; progress and compact failure feedback share the remaining space.
 */
@Composable
internal fun WearModuleDownloadScreen(name: String, state: ModuleDownloadUiState, fraction: () -> Float, iconUrl: String? = null, iconOrigin: String? = null) {
    val failed = state.phase == ModuleDownloadPhase.FAILED || state.phase == ModuleDownloadPhase.VERIFY_FAILED
    val sizeKnown = state.totalBytes > 0L
    val reduceMotion = LocalReduceMotion.current
    val failedProgress = remember { Animatable(0f) }
    LaunchedEffect(failed, reduceMotion) {
        if (failed) {
            failedProgress.snapTo(fraction())
            failedProgress.animateTo(1f, if (reduceMotion) snap() else tween(200))
        } else {
            failedProgress.snapTo(0f)
        }
    }
    val failureTitle = stringResource(R.string.download_failed_title)
    Box(Modifier.fillMaxSize()) {
        ScreenScaffold(contentPadding = PaddingValues(0.dp), timeText = { Box(Modifier.testTag("module-download-clock")) { WearTimeText(forceTime = true) } }, overscrollEffect = null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val nameStyle = MaterialTheme.typography.titleMedium
                val statusStyle = MaterialTheme.typography.bodyMedium
                val percentStyle = MaterialTheme.typography.titleMedium
                val textMeasurer = rememberTextMeasurer()
                val textWidth = with(density) { (maxWidth * 0.72f).roundToPx() }
                val constraints = Constraints(maxWidth = textWidth)
                val downloading = stringResource(R.string.wear_downloading)
                val verifying = stringResource(R.string.wear_verifying_download)
                val progressText = if (state.fraction != null) {
                    stringResource(R.string.wear_download_percent, ((state.fraction ?: 0f) * 100).toInt())
                } else if (sizeKnown) {
                    stringResource(R.string.wear_download_size, formatFileSize(state.downloadedBytes), formatFileSize(state.totalBytes))
                } else {
                    formatFileSize(state.downloadedBytes)
                }
                // Measure the actual fonts (including fallback glyphs and bold) rather than treating
                // the declared line heights as hard bounds. Both outcomes retain one shared slot.
                val statusPixels = maxOf(
                    textMeasurer.measure(downloading, statusStyle, constraints = constraints).size.height,
                    textMeasurer.measure(verifying, statusStyle, constraints = constraints).size.height,
                )
                val percentPixels = textMeasurer.measure(
                    progressText,
                    percentStyle.copy(fontWeight = FontWeight.Bold),
                    constraints = constraints,
                ).size.height
                val failurePixels = textMeasurer.measure(
                    "$downloading\n$verifying",
                    statusStyle,
                    constraints = constraints,
                    maxLines = 2,
                ).size.height
                val statusHeight = with(density) { maxOf(statusPixels + percentPixels + 2.dp.roundToPx(), failurePixels).toDp() }
                val nameHeight = textMeasurer.measure(name, nameStyle.copy(fontWeight = FontWeight.Bold), maxLines = 2, constraints = constraints).size.height
                val textHeight = with(density) { nameHeight.toDp() } + statusHeight + 12.dp
                val iconSize = minOf(maxWidth * 0.32f, (maxHeight * 0.78f - textHeight).coerceAtLeast(32.dp))
                Column(
                    Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = maxWidth * 0.14f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.testTag("module-download-icon")) {
                        WearCatalogModuleIcon(iconUrl, iconOrigin, iconSize)
                    }
                    Text(name, modifier = Modifier.fillMaxWidth(), style = nameStyle, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.fillMaxWidth().height(statusHeight), contentAlignment = Alignment.Center) {
                        if (failed) {
                            Text(
                                moduleDownloadFailureText(state),
                                style = statusStyle,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(if (state.phase == ModuleDownloadPhase.VERIFYING) R.string.wear_verifying_download else R.string.wear_downloading),
                                    style = statusStyle,
                                    maxLines = 1,
                                )
                                Text(
                                    progressText,
                                    style = percentStyle,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
        val ringColors = ProgressIndicatorDefaults.colors(indicatorColor = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        val ringStroke = CircularProgressIndicatorDefaults.smallStrokeWidth
        // Use the M3 custom-animation API: its animated wrapper retains the first progress lambda
        // and adds its own animation, which would hide this attempt's failure transition.
        Spacer(
            Modifier.fillMaxSize().padding(CircularProgressIndicatorDefaults.FullScreenPadding)
                .testTag("module-download-ring").semantics {
                    if (failed) {
                        contentDescription = failureTitle
                    } else {
                        progressBarRangeInfo = state.fraction?.let { ProgressBarRangeInfo(it, 0f..1f) } ?: ProgressBarRangeInfo.Indeterminate
                    }
                }.drawBehind {
                    drawCircularProgressIndicator(
                        progress = if (failed) failedProgress.value else fraction(),
                        colors = ringColors,
                        strokeWidth = ringStroke,
                        startAngle = 300f,
                        endAngle = 240f,
                    )
                },
        )
    }
}

@Composable
private fun moduleDownloadFailureText(state: ModuleDownloadUiState): String {
    val verification = state.verificationFailure
    val failure = state.failure
    return when {
        verification == DownloadVerificationFailure.URL_NOT_ALLOWED -> stringResource(R.string.wear_download_url_blocked)
        verification != null -> stringResource(R.string.wear_download_verify_failed)
        state.phase == ModuleDownloadPhase.VERIFY_FAILED -> stringResource(R.string.wear_download_verify_failed)
        failure?.reason == DownloadFailureReason.HTTP && failure.httpStatus != null -> stringResource(R.string.wear_download_error_http, failure.httpStatus)
        failure != null && failure.reason != DownloadFailureReason.HTTP -> stringResource(failure.reason.resId)
        else -> stringResource(R.string.wear_download_error_unknown)
    }
}

/** The existing repository sort option, shared by advanced search and the operations panel. */
@Composable
internal fun WearModuleRepoSortPage(viewModel: ModuleRepoViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_sort)) }
        item {
            WearSettingsSwitchWidget(spec, stringResource(R.string.module_sort_star_first), state.sortStargazerCountFirst, { viewModel.dispatch(ModuleRepoUiAction.SetStarsFirst(it)) }, icon = Icons.Rounded.Star)
        }
    }
}
