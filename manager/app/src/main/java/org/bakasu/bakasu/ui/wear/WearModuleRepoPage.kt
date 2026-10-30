package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.DocumentFormat
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.model.RepositoryError
import org.bakasu.bakasu.domain.model.RepositoryFailure
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.viewmodel.ModuleDetailUiAction
import org.bakasu.bakasu.ui.viewmodel.ModuleDetailViewModel
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadPhase
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadUiState
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadViewModel
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoUiAction
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoViewModel
import org.bakasu.bakasu.ui.viewmodel.formatFileSize
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearDialogIcon
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearIconAction
import org.bakasu.bakasu.ui.wear.component.WearIconButtonGroup
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearResult
import org.bakasu.bakasu.ui.wear.component.WearResultDialog
import org.bakasu.bakasu.ui.wear.component.WearScaledItem
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearTextInputPage
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun WearModuleRepoPage(viewModel: ModuleRepoViewModel, onBack: () -> Unit, onSearch: () -> Unit, onOpen: (CatalogModule) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Opening the repository loads its index, which also checks that the watch can reach it over
    // any network: the paired phone, the watch Wi-Fi or its mobile data.
    var failure by remember { mutableStateOf<RepositoryFailure?>(null) }
    var refreshRequest by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    fun retry() {
        failure = null
        refreshRequest++
    }
    val failureReason = stringResource(if (failure?.reason == RepositoryError.OFFLINE) R.string.wear_no_internet else R.string.wear_repo_unreachable)
    // Without a list there is nothing to show, so the failure asks to retry or leave.
    val connectDialog = rememberCustomDialog { dismiss ->
        val leave = {
            dismiss()
            onBack()
        }
        AlertDialog(
            visible = true,
            onDismissRequest = leave,
            icon = { WearDialogIcon(Icons.Rounded.ErrorOutline) },
            title = { Text(stringResource(R.string.wear_repo_connect_failed)) },
            text = { Text(failureReason, color = MaterialTheme.colorScheme.error) },
            confirmButton = {
                AlertDialogDefaults.ConfirmButton(
                    onClick = {
                        dismiss()
                        retry()
                    },
                )
            },
            dismissButton = { AlertDialogDefaults.DismissButton(onClick = leave) },
        )
    }
    LaunchedEffect(viewModel, state.repositoryUrl, refreshRequest) {
        failure = null
        failure = viewModel.refreshAndGetFailure()
        if (failure != null && state.modules.isEmpty() && state.search.isBlank()) connectDialog.show()
    }
    // A search that could not load shows the failure in place of its results; Back clears the search.
    if (failure != null && state.search.isNotBlank() && state.modules.isEmpty() && !state.isRefreshing) {
        BackHandler {
            failure = null
            viewModel.dispatch(ModuleRepoUiAction.Search(""))
        }
        ScreenScaffold {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    stringResource(R.string.wear_load_failed),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Text(
                    failureReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }
    WearResultDialog(WearResult(failureReason, success = false, brief = true).takeIf { failure != null && state.modules.isNotEmpty() }) {
        failure = null
    }
    WearList(
        isLoading = state.isRefreshing && state.modules.isEmpty(),
        isRefreshing = state.isRefreshing,
        onRefresh = ::retry,
        onBack = onBack,
    ) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.module_repo)) }
        item { WearSettingsJumpPageWidget(spec, stringResource(R.string.search_modules), onSearch, icon = Icons.Rounded.Search, description = state.search.ifBlank { null }) }
        item { WearSettingsSwitchWidget(spec, stringResource(R.string.module_sort_star_first), state.sortStargazerCountFirst, { viewModel.dispatch(ModuleRepoUiAction.SetStarsFirst(it)) }) }
        if (state.modules.isEmpty() && !state.isRefreshing && failure == null) {
            item {
                WearEmptyState(
                    spec,
                    if (state.search.isNotBlank()) Icons.Rounded.SearchOff else Icons.Rounded.Cloud,
                    stringResource(R.string.wear_catalog_empty),
                )
            }
        }
        LazySegmentedColumn(state.modules, { it.catalogId }) { module ->
            WearSettingsJumpPageWidget(
                spec,
                module.moduleName,
                { onOpen(module) },
                icon = Icons.Rounded.Cloud,
                description = module.summary.ifBlank { module.moduleId },
            )
        }
    }
}

/** The repository search input, a route of its own; it searches the list it was opened from. */
@Composable
internal fun WearModuleRepoSearchPage(viewModel: ModuleRepoViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WearTextInputPage(stringResource(R.string.search_modules), state.search) {
        viewModel.dispatch(ModuleRepoUiAction.Search(it.trim()))
        onBack()
        // A search before the repository has loaded tries to load it first.
        if (state.modules.isEmpty() && !state.isRefreshing) viewModel.dispatch(ModuleRepoUiAction.Refresh)
    }
}

/**
 * A repository module's data, shared by its page and section routes so a section opens without
 * loading the module again; each route has its own ViewModel store, so it lives in the activity's.
 */
@Composable
private fun catalogModuleViewModel(moduleId: String, repositoryUrl: String): ModuleDetailViewModel = koinViewModel(
    viewModelStoreOwner = LocalActivity.current as ComponentActivity,
    key = "wear-catalog-$repositoryUrl#$moduleId",
    parameters = { parametersOf(moduleId, repositoryUrl) },
)

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
    WearList(isLoading = state.loading, onBack = onBack) { spec ->
        if (module == null) item { WearPageHeader(spec, stringResource(R.string.module_repo)) }
        if (state.error != null) item { WearActionButton(spec, Icons.Rounded.Cloud, stringResource(R.string.network_retry), { viewModel.dispatch(ModuleDetailUiAction.Retry) }) }
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
    LaunchedEffect(module?.catalogId, module?.detailLoaded, module?.registryMetadata?.buildId) {
        if (section == "readme" && module?.detailLoaded == true) viewModel.loadReadme()
    }
    val readmeLoading = section == "readme" && module != null && (!module.detailLoaded || readme == null || readme.loading) && state.error == null
    WearList(isLoading = state.loading || readmeLoading, onBack = onBack) { spec ->
        item { WearPageHeader(spec, module?.moduleName ?: stringResource(R.string.module_repo)) }
        if (state.error != null) item { WearActionButton(spec, Icons.Rounded.Cloud, stringResource(R.string.network_retry), { viewModel.dispatch(ModuleDetailUiAction.Retry) }) }
        if (module != null) {
            when (section) {
                "readme" -> when {
                    readmeLoading -> Unit

                    readme?.failed == true -> item {
                        WearActionButton(spec, Icons.Rounded.Cloud, stringResource(R.string.network_retry), { viewModel.loadReadme(retry = true) })
                    }

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
 * The download of one module file. It starts once when the route opens; leaving the route cancels
 * a running download, as its ViewModel belongs to the route. A verified file goes to [onDownloaded].
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
 * ring reads [fraction] when it draws, so it follows the transfer; without a known size it spins.
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
                } else {
                    CircularProgressIndicator(ringModifier, strokeWidth = CircularProgressIndicatorDefaults.largeStrokeWidth)
                }
            }
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WearModuleIcon(48.dp)
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
