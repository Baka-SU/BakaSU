package org.bakasu.bakasu.ui.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.Code
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Person
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Warning
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.DownloadStatus
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.usecase.EnqueueDownloadUseCase
import org.bakasu.bakasu.domain.usecase.ObserveDownloadUseCase
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.viewmodel.ModuleDetailUiAction
import org.bakasu.bakasu.ui.viewmodel.ModuleDetailViewModel
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoUiAction
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoUiEvent
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoViewModel
import org.bakasu.bakasu.ui.viewmodel.formatFileSize
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearSubPage
import org.bakasu.bakasu.ui.wear.component.WearTextInputPage
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun WearModuleRepoPage(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val viewModel = koinViewModel<ModuleRepoViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    // Opening the repository loads its index, which also checks that the watch can reach it over
    // any network: the paired phone, the watch Wi-Fi or its mobile data.
    var failure by remember { mutableStateOf<ModuleRepoUiEvent?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.dispatch(ModuleRepoUiAction.Refresh)
        viewModel.events.collect { failure = it }
    }
    fun retry() {
        failure = null
        viewModel.dispatch(ModuleRepoUiAction.Refresh)
    }
    val failureReason = stringResource(if (failure is ModuleRepoUiEvent.Offline) R.string.wear_no_internet else R.string.wear_repo_unreachable)
    // Without a list there is nothing to show, so the failure asks to retry or leave.
    val connectDialog = rememberCustomDialog { dismiss ->
        val leave = {
            dismiss()
            onBack()
        }
        AlertDialog(
            visible = true,
            onDismissRequest = leave,
            icon = { Icon(Icons.TwoTone.Info, contentDescription = null) },
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
    LaunchedEffect(failure) {
        if (failure != null && state.modules.isEmpty() && state.search.isBlank()) connectDialog.show()
    }
    if (searching) {
        WearSubPage({ searching = false }) {
            WearTextInputPage(stringResource(R.string.search_modules), state.search) {
                viewModel.dispatch(ModuleRepoUiAction.Search(it.trim()))
                searching = false
                // A search before the repository has loaded tries to load it first.
                if (state.modules.isEmpty() && !state.isRefreshing) retry()
            }
        }
        return
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
                    Icons.TwoTone.Warning,
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
    WearList(
        isLoading = state.isRefreshing && state.modules.isEmpty(),
        isRefreshing = state.isRefreshing,
        onRefresh = ::retry,
        onBack = onBack,
    ) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.module_repo)) }
        item { WearSettingsJumpPageWidget(spec, stringResource(R.string.search_modules), { searching = true }, icon = Icons.TwoTone.Search, description = state.search.ifBlank { null }) }
        item { WearSettingsSwitchWidget(spec, stringResource(R.string.module_sort_star_first), state.sortStargazerCountFirst, { viewModel.dispatch(ModuleRepoUiAction.SetStarsFirst(it)) }) }
        if (failure != null && state.modules.isNotEmpty()) item { WearInfoCard(spec) { Text(failureReason) } }
        if (state.modules.isEmpty() && !state.isRefreshing && failure == null) item { WearInfoCard(spec) { Text(stringResource(R.string.wear_catalog_empty)) } }
        LazySegmentedColumn(state.modules, { it.moduleId }) { module ->
            WearSettingsJumpPageWidget(
                spec,
                module.moduleName,
                { onOpen(module.moduleId) },
                icon = Icons.TwoTone.Cloud,
                description = module.summary.ifBlank { module.moduleId },
            )
        }
    }
}

@Composable
internal fun WearOnlineModulePage(moduleId: String, onBack: () -> Unit, onLink: (String) -> Unit, onInstall: (String) -> Unit) {
    val viewModel = koinViewModel<ModuleDetailViewModel>(key = "wear-catalog-$moduleId", parameters = { parametersOf(moduleId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val module = state.module
    var page by rememberSaveable { mutableStateOf("") }
    val enqueue = koinInject<EnqueueDownloadUseCase>()
    val observe = koinInject<ObserveDownloadUseCase>()
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Int?>(null) }
    var failed by remember { mutableStateOf(false) }
    fun download(asset: ModuleReleaseAsset) {
        if (progress != null) return
        progress = 0
        failed = false
        scope.launch {
            runCatching {
                val id = enqueue(asset.downloadUrl, asset.name)
                observe(id).filterNotNull().onEach { progress = it.progress }
                    .first { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }
            }.onSuccess { result ->
                result.resultUri?.let(onInstall) ?: run { failed = true }
            }.onFailure { failed = true }
            progress = null
        }
    }
    val close = { if (page.isNotEmpty()) page = "" else onBack() }
    val content: @Composable () -> Unit = {
        WearList(isLoading = state.loading, onBack = close) { spec ->
            item { WearPageHeader(spec, module?.moduleName ?: stringResource(R.string.module_repo)) }
            if (state.error != null) item { WearActionButton(spec, Icons.TwoTone.Cloud, stringResource(R.string.network_retry), { viewModel.dispatch(ModuleDetailUiAction.Retry) }) }
            if (failed) item { WearInfoCard(spec) { Text(stringResource(R.string.operation_failed)) } }
            progress?.let { percent -> item { WearInfoCard(spec) { Text(stringResource(R.string.wear_download_progress, percent)) } } }
            if (module != null) {
                when (page) {
                    "readme" -> {
                        val text = HtmlCompat.fromHtml(module.readme, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                        text.chunked(600).forEach { chunk -> item { WearInfoCard(spec) { Text(chunk, style = MaterialTheme.typography.bodySmall) } } }
                    }

                    "releases", "install" -> {
                        module.releases.forEach { release ->
                            item { WearSectionHeader(spec, release.name.ifBlank { release.tagName }) }
                            if (page == "releases") {
                                item {
                                    WearInfoCard(spec) {
                                        Text(release.publishedAt, style = MaterialTheme.typography.labelSmall)
                                        Text(HtmlCompat.fromHtml(release.descriptionHTML, HtmlCompat.FROM_HTML_MODE_COMPACT).toString(), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            items(release.assets, key = { it.downloadUrl }) { asset ->
                                WearActionButton(
                                    spec,
                                    Icons.TwoTone.Download,
                                    asset.name,
                                    { download(asset) },
                                    enabled = progress == null,
                                    secondaryText = stringResource(R.string.assert_support_content, formatFileSize(asset.size), asset.downloadCount),
                                )
                            }
                        }
                    }

                    "info" -> item {
                        WearInfoCard(spec) {
                            Text(module.moduleId, style = MaterialTheme.typography.titleSmall)
                            Text(module.summary, style = MaterialTheme.typography.bodySmall)
                            Text(module.authors, style = MaterialTheme.typography.bodySmall)
                            Text(module.latestRelease, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    else -> {
                        item { WearSectionHeader(spec, stringResource(R.string.wear_module_data)) }
                        SegmentedColumn(listOf("readme" to R.string.readme, "releases" to R.string.release, "info" to R.string.info), { it.first }) { (target, label) ->
                            WearSettingsJumpPageWidget(spec, stringResource(label), { page = target }, icon = Icons.TwoTone.Info)
                        }
                        item { WearSectionHeader(spec, stringResource(R.string.wear_authors_source)) }
                        LazySegmentedColumn(module.authorList.filter { it.link.isNotBlank() }, { it.link }) { author ->
                            WearSettingsJumpPageWidget(spec, author.name, { onLink(author.link) }, icon = Icons.TwoTone.Person)
                        }
                        if (module.sourceUrl.isNotBlank()) item { WearSettingsJumpPageWidget(spec, stringResource(R.string.source_code), { onLink(module.sourceUrl) }, icon = Icons.TwoTone.Code) }
                        item { WearSectionHeader(spec, stringResource(R.string.wear_operations)) }
                        item { WearActionButton(spec, Icons.TwoTone.Download, stringResource(R.string.wear_install_download), { page = "install" }, enabled = module.releases.any { it.assets.isNotEmpty() }, colors = ButtonDefaults.buttonColors()) }
                    }
                }
            }
        }
    }
    if (page.isNotEmpty()) WearSubPage(close, content) else content()
}
