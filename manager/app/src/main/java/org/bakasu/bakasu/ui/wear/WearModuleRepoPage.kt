package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.DocumentFormat
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
import org.bakasu.bakasu.ui.wear.component.WearDialogIcon
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearIconAction
import org.bakasu.bakasu.ui.wear.component.WearIconButtonGroup
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearLoadFailureScreen
import org.bakasu.bakasu.ui.wear.component.WearLoadingIndicator
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearScaledItem
import org.bakasu.bakasu.ui.wear.component.WearSearchPage
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.bakasu.bakasu.ui.wear.component.wearRepositoryFailureText
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun WearModuleRepoPage(
    viewModel: ModuleRepoViewModel,
    onBack: () -> Unit,
    onOpenPanel: () -> Unit,
    hasOperationsPanel: Boolean = false,
    onOpen: (CatalogModule) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WearModuleRepoPage(state, { viewModel.refresh(fromPull = true) }, onBack, onOpenPanel, hasOperationsPanel, onOpen)
}

@Composable
internal fun WearModuleRepoPage(
    state: ModuleRepoUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenPanel: () -> Unit,
    hasOperationsPanel: Boolean = false,
    onOpen: (CatalogModule) -> Unit,
) {
    val failed = !state.isRefreshing && state.content == CatalogContent.FAILED
    WearList(
        isLoading = state.isRefreshing && state.modules.isEmpty() && !state.isPullRefreshing,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        onBack = onBack,
        onConfirm = if (failed) onRefresh else null,
        confirmLabel = if (failed) stringResource(R.string.network_retry) else null,
        backToTop = !failed,
        onOpenPanel = onOpenPanel,
        hasOperationsPanel = hasOperationsPanel,
        panelLabel = stringResource(R.string.wear_operations),
    ) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.module_repo)) }
        if (failed) {
            item {
                WearEmptyState(spec, Icons.Rounded.Error, stringResource(R.string.wear_load_failed))
            }
            item {
                WearInfoCard(spec) {
                    Text(wearRepositoryFailureText(state.failures[state.repositoryUrl]), textAlign = TextAlign.Center)
                }
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
                icon = Icons.Rounded.Extension,
                description = module.summary.ifBlank { module.moduleId },
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
private fun rememberModuleDownload(moduleName: String, installer: WearModuleInstaller): (ModuleRelease, ModuleReleaseAsset) -> Unit {
    var pendingRelease by remember { mutableStateOf<ModuleRelease?>(null) }
    var pendingAsset by remember { mutableStateOf<ModuleReleaseAsset?>(null) }
    val thenInstall = stringResource(R.string.wear_download_then_install)
    val confirmDialog = rememberCustomDialog { dismiss ->
        val asset = pendingAsset
        AlertDialog(
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
                        if (release != null && asset != null) installer.download(moduleName, release, asset)
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
    val requestDownload = rememberModuleDownload(module?.moduleName.orEmpty(), installer)
    if (!state.loading && (state.error != null || state.repositoryFailure != null)) {
        WearLoadFailureScreen(
            if (state.error == ModuleCatalogFailure.NotFound) stringResource(R.string.repo_module_unavailable) else wearRepositoryFailureText(state.repositoryFailure),
            onBack,
            { viewModel.dispatch(ModuleDetailUiAction.Retry) },
        )
        return
    }
    WearList(isLoading = state.loading && module == null, isRefreshing = state.loading && module != null, onBack = onBack) { spec ->
        if (module == null) item { WearPageHeader(spec, stringResource(R.string.module_repo)) }
        if (module != null) {
            // As in the design: the module icon, name and authors, then its main actions. As the
            // first item the icon takes the card's minimum list padding, a share of the screen
            // height, so it stays below the time text on any screen size.
            item {
                WearScaledItem(spec, Modifier.fillMaxWidth().minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        WearModuleIcon(52.dp)
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
    val requestDownload = rememberModuleDownload(module?.moduleName.orEmpty(), installer)
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
        )
        return
    }
    val loading = state.loading || readmeLoading
    val hasContent = module != null && (section != "readme" || readme?.document != null)
    WearList(isLoading = loading && !hasContent, isRefreshing = loading && hasContent, onBack = onBack) { spec ->
        item { WearPageHeader(spec, module?.moduleName ?: stringResource(R.string.module_repo)) }
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
        if (viewModel.state.value.phase == null) viewModel.start(asset)
    }
    LaunchedEffect(download.resultUri) {
        download.resultUri?.let(onDownloaded)
    }
    WearModuleDownloadScreen(
        route.name,
        route.version,
        download,
        // Read through the state when the ring draws, not captured once.
        fraction = { downloadState.value.fraction ?: 1f.takeIf { downloadState.value.phase == ModuleDownloadPhase.VERIFYING } ?: 0f },
    ) { viewModel.start(asset) }
}

/** The default module icon on a rounded tonal tile, standing in for a module's own icon. */
@Composable
private fun WearModuleIcon(size: Dp) {
    val glyph = size * 0.55f
    Box(
        Modifier.size(size).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        // The puzzle piece's body sits lower left in its 24dp grid (x 2-19, y 5-22), with only thin
        // tabs to the top and right, so it is moved by 1.2/24 of its size to look centered on the tile.
        Icon(
            Icons.Rounded.Extension,
            contentDescription = null,
            modifier = Modifier.offset(x = glyph * 0.05f, y = -glyph * 0.05f).size(glyph),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * A module download as in the design: the progress runs around the screen edge, leaving the time at
 * the top, with the module, its state, the percentage and the downloaded size in the center. The
 * ring reads [fraction] when it draws, so it follows the transfer; without a known size it shows the loading animation.
 * A failure says why and offers to retry; swiping back leaves.
 */
@Composable
private fun WearModuleDownloadScreen(name: String, version: String, state: ModuleDownloadUiState, fraction: () -> Float, onRetry: () -> Unit) {
    val failed = state.phase == ModuleDownloadPhase.FAILED || state.phase == ModuleDownloadPhase.VERIFY_FAILED
    val sizeKnown = state.totalBytes > 0L
    ScreenScaffold {
        Box(Modifier.fillMaxSize()) {
            val ringModifier = Modifier.fillMaxSize().padding(CircularProgressIndicatorDefaults.FullScreenPadding)
            if (!failed) {
                if (sizeKnown || state.phase == ModuleDownloadPhase.VERIFYING) {
                    CircularProgressIndicator(progress = fraction, modifier = ringModifier, startAngle = 300f, endAngle = 240f)
                }
            }
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!failed && !sizeKnown && state.phase != ModuleDownloadPhase.VERIFYING) WearLoadingIndicator() else WearModuleIcon(48.dp)
                // The name is the focus; the state and its numbers follow in smaller, quieter styles.
                Text(
                    name,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (version.isNotBlank()) {
                    Text(
                        version,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when {
                    failed -> {
                        Text(
                            stringResource(if (state.phase == ModuleDownloadPhase.VERIFY_FAILED) R.string.wear_download_verify_failed else R.string.download_failed_title),
                            modifier = Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        CompactButton(
                            onClick = onRetry,
                            modifier = Modifier.padding(top = 6.dp),
                            icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                            label = { Text(stringResource(R.string.network_retry)) },
                        )
                    }

                    state.phase == ModuleDownloadPhase.VERIFYING -> Text(
                        stringResource(R.string.wear_verifying_download),
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> {
                        Text(
                            stringResource(R.string.wear_downloading),
                            modifier = Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // A percentage only exists when the server announced the size.
                        state.fraction?.let { share ->
                            Text(
                                stringResource(R.string.wear_download_percent, (share * 100).toInt()),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            if (sizeKnown) {
                                stringResource(R.string.wear_download_size, formatFileSize(state.downloadedBytes), formatFileSize(state.totalBytes))
                            } else {
                                formatFileSize(state.downloadedBytes)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** The existing repository sort option, shared by advanced search and the operations panel. */
@Composable
internal fun WearModuleRepoSortPage(viewModel: ModuleRepoViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_sort)) }
        item {
            WearSettingsSwitchWidget(spec, stringResource(R.string.module_sort_star_first), state.sortStargazerCountFirst, { viewModel.dispatch(ModuleRepoUiAction.SetStarsFirst(it)) })
        }
    }
}
