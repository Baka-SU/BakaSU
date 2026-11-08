package org.bakasu.bakasu.ui.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.domain.model.ModuleUpdateMetadata
import org.bakasu.bakasu.ui.viewmodel.ExecuteModuleActionUiAction
import org.bakasu.bakasu.ui.viewmodel.ExecuteModuleActionUiEvent
import org.bakasu.bakasu.ui.viewmodel.ExecuteModuleActionViewModel
import org.bakasu.bakasu.ui.viewmodel.ModuleChangelogViewModel
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearChip
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearInfoRow
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearLogPage
import org.bakasu.bakasu.ui.wear.component.WearModuleInfoCard
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearResult
import org.bakasu.bakasu.ui.wear.component.WearResultDialog
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone
import org.bakasu.bakasu.ui.wear.component.toLogLines
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The changelog opens separately, so its length and availability do not block the download.
 * [onDownload] downloads the ZIP on the shared module download page, which installs it.
 */
@Composable
internal fun WearModuleUpdatePage(
    module: InstalledModule?,
    onBack: () -> Unit,
    onDownload: (InstalledModule, ModuleUpdateMetadata) -> Unit,
    onChangelog: () -> Unit,
) {
    val update = module?.moduleUpdate
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.module_update)) }
        if (module != null && update != null) {
            // The module and its version change lead, followed by the two actions.
            item {
                WearModuleInfoCard(spec, compact = true) {
                    WearInfoRow(
                        icon = Icons.Rounded.Update,
                        label = module.name,
                        value = stringResource(R.string.wear_update_version, module.version.ifBlank { stringResource(R.string.unknown) }, update.version),
                        labelStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
            item {
                WearActionButton(
                    spec,
                    Icons.Rounded.Article,
                    stringResource(R.string.module_changelog),
                    onChangelog,
                    enabled = update.changelog.isNotBlank(),
                    secondaryText = stringResource(if (update.changelog.isBlank()) R.string.wear_update_changelog_unavailable else R.string.wear_update_changelog_summary),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                )
            }
            item {
                WearActionButton(
                    spec,
                    Icons.Rounded.Download,
                    stringResource(R.string.wear_update_download),
                    { onDownload(module, update) },
                    enabled = !module.remove && update.zipUrl.isNotBlank(),
                    secondaryText = stringResource(R.string.wear_update_to, update.version),
                )
            }
        }
    }
}

@Composable
internal fun WearModuleChangelogPage(module: InstalledModule?, onBack: () -> Unit) {
    val url = module?.moduleUpdate?.changelog.orEmpty()
    val viewModel = koinViewModel<ModuleChangelogViewModel>(key = url, parameters = { parametersOf(url) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    WearList(isLoading = state.loading, onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.module_changelog)) }
        when {
            state.loading -> Unit

            state.error != null -> {
                item {
                    WearStatusItem(spec, Icons.Rounded.Error, stringResource(R.string.module_changelog_failed, state.error.orEmpty()), tone = WearStatusTone.ERROR)
                }
                item {
                    WearActionButton(spec, Icons.Rounded.Download, stringResource(R.string.network_retry), viewModel::load)
                }
            }

            state.content.isBlank() -> item {
                WearStatusItem(spec, Icons.Rounded.Article, stringResource(R.string.wear_update_changelog_unavailable))
            }

            else -> changelogChunks(state.content).forEach { chunk ->
                item {
                    WearInfoCard(spec) {
                        Text(chunk, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Splits a changelog into list items at line breaks, so no line is cut and the original line
 * structure stays; only a single line longer than [limit] is split within itself.
 */
private fun changelogChunks(text: String, limit: Int = 600): List<String> {
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    text.trim().lines().forEach { line ->
        line.chunked(limit).ifEmpty { listOf("") }.forEach { part ->
            if (current.isNotEmpty() && current.length + part.length + 1 > limit) {
                chunks += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append('\n')
            current.append(part)
        }
    }
    chunks += current.toString()
    return chunks.map { it.trim('\n') }.filter(String::isNotBlank)
}

/**
 * Runs a module's action script with the phone's ExecuteModuleActionViewModel: the status shows
 * the latest output line, the full output opens as its own log page, and saving goes through the
 * shared log use case once the script has finished.
 */
@Composable
internal fun WearExecuteModulePage(moduleId: String, onBack: () -> Unit, onCompleted: () -> Unit) {
    val viewModel = koinViewModel<ExecuteModuleActionViewModel>(parameters = { parametersOf(moduleId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    var successful by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<WearResult?>(null) }
    val saved = WearResult(stringResource(R.string.log_saved), success = true, brief = true)
    val failed = WearResult(stringResource(R.string.operation_failed), success = false, brief = true)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExecuteModuleActionUiEvent.Completed -> {
                    successful = event.successful
                    onCompleted()
                }

                is ExecuteModuleActionUiEvent.LogSaved -> result = saved

                is ExecuteModuleActionUiEvent.Error -> result = failed
            }
        }
    }
    val lines = remember(state.output) { state.output.toLogLines() }
    val fullLog = stringResource(R.string.wear_full_log)

    if (showLog) {
        WearLogPage(fullLog, lines, following = state.running) { showLog = false }
        return
    }

    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_execute_action)) }
        item { WearSectionHeader(spec, stringResource(R.string.wear_execution_status)) }
        item {
            WearChip(
                spec,
                stringResource(
                    when (successful) {
                        true -> R.string.module_action_success
                        false -> R.string.operation_failed
                        null -> R.string.wear_running
                    },
                ),
                secondaryLabel = lines.lastOrNull { it.isNotBlank() },
                icon = when (successful) {
                    true -> Icons.Rounded.CheckCircle
                    false -> Icons.Rounded.Error
                    null -> Icons.Rounded.PlayArrow
                },
            )
        }
        item {
            WearActionButton(
                spec,
                Icons.Rounded.Article,
                fullLog,
                { showLog = true },
                secondaryText = stringResource(R.string.wear_install_log_summary),
            )
        }
        item { WearSectionHeader(spec, stringResource(R.string.wear_operations)) }
        // The view model saves the log only after the script has finished, as on the phone.
        item {
            WearActionButton(
                spec,
                Icons.Rounded.Save,
                stringResource(R.string.wear_save_output),
                { viewModel.dispatch(ExecuteModuleActionUiAction.SaveLog) },
                enabled = !state.running,
                secondaryText = stringResource(R.string.wear_save_output_summary),
            )
        }
    }
    WearResultDialog(result) { result = null }
}
