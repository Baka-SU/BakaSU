package org.bakasu.bakasu.ui.wear

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Smartphone
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.wear.compose.material3.lazy.transformedHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.file.ZipFileDetector
import org.bakasu.bakasu.data.file.ZipFileInfo
import org.bakasu.bakasu.domain.model.DownloadStatus
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.domain.usecase.EnqueueDownloadUseCase
import org.bakasu.bakasu.domain.usecase.FetchRemoteTextUseCase
import org.bakasu.bakasu.domain.usecase.ObserveDownloadUseCase
import org.bakasu.bakasu.ui.viewmodel.ExecuteModuleActionUiAction
import org.bakasu.bakasu.ui.viewmodel.ExecuteModuleActionUiEvent
import org.bakasu.bakasu.ui.viewmodel.ExecuteModuleActionViewModel
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearChip
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearLogPage
import org.bakasu.bakasu.ui.wear.component.WearModuleInfoCard
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearScaledItem
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone
import org.bakasu.bakasu.ui.wear.component.toLogLines
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A module update with the phone's use cases: the changelog is read first, then the ZIP downloads
 * and [onReady] installs it.
 */
@Composable
internal fun WearModuleUpdatePage(module: InstalledModule?, onBack: () -> Unit, onReady: (String) -> Unit) {
    val fetchRemoteText = koinInject<FetchRemoteTextUseCase>()
    val enqueueDownload = koinInject<EnqueueDownloadUseCase>()
    val observeDownload = koinInject<ObserveDownloadUseCase>()
    val scope = rememberCoroutineScope()
    var changelog by rememberSaveable { mutableStateOf<String?>(null) }
    var changelogError by rememberSaveable { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var failed by remember { mutableStateOf(false) }
    val update = module?.moduleUpdate
    LaunchedEffect(update?.changelog) {
        val url = update?.changelog ?: return@LaunchedEffect
        if (changelog == null) {
            fetchRemoteText(url).fold(
                onSuccess = { changelog = it },
                onFailure = { changelogError = it.message.orEmpty() },
            )
        }
    }
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.module_update)) }
        if (module != null && update != null) {
            // The module and its version change lead; the changelog follows as quieter body text.
            item {
                WearModuleInfoCard(spec) {
                    Text(
                        module.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.wear_update_version, module.version.ifBlank { stringResource(R.string.unknown) }, update.version),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { WearSectionHeader(spec, stringResource(R.string.module_changelog)) }
            when {
                changelogError != null -> item {
                    WearStatusItem(
                        spec,
                        Icons.TwoTone.Error,
                        stringResource(R.string.module_changelog_failed, changelogError.orEmpty()),
                        tone = WearStatusTone.ERROR,
                    )
                }

                changelog == null -> item {
                    WearScaledItem(spec) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center).size(ButtonDefaults.IconSize))
                    }
                }

                // The whole changelog, split only at line breaks so each list item stays short.
                else -> changelogChunks(changelog.orEmpty()).forEach { chunk ->
                    item {
                        WearInfoCard(spec) {
                            Text(chunk, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            // Like the phone, the update is offered only after its changelog has been read. The
            // button shows its own progress while the ZIP downloads, and ignores taps meanwhile.
            item {
                val downloading = progress != null
                Button(
                    onClick = {
                        if (progress != null) return@Button
                        failed = false
                        progress = 0
                        scope.launch {
                            val id = enqueueDownload(update.zipUrl, "${module.name}-${update.version}.zip")
                            val result = observeDownload(id).filterNotNull().onEach { progress = it.progress }
                                .first { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }
                            progress = null
                            result.resultUri?.let(onReady) ?: run { failed = true }
                        }
                    },
                    enabled = changelog != null,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                    transformation = SurfaceTransformation(spec),
                    icon = {
                        if (downloading) {
                            // The ring reads the progress state when it draws, so it follows the download.
                            CircularProgressIndicator(
                                progress = { (progress ?: 0) / 100f },
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                colors = ProgressIndicatorDefaults.colors(indicatorColor = LocalContentColor.current),
                                strokeWidth = CircularProgressIndicatorDefaults.smallStrokeWidth,
                            )
                        } else {
                            Icon(Icons.TwoTone.Download, contentDescription = null)
                        }
                    },
                    secondaryLabel = {
                        Text(
                            when {
                                downloading -> stringResource(R.string.wear_download_percent, progress ?: 0)
                                failed -> stringResource(R.string.wear_update_retry)
                                changelogError != null -> stringResource(R.string.wear_update_changelog_unavailable)
                                changelog == null -> stringResource(R.string.wear_update_reading_changelog)
                                else -> stringResource(R.string.wear_update_to, update.version)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                ) {
                    Text(
                        stringResource(if (downloading) R.string.wear_update_downloading else R.string.wear_update_download),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
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
    var logSaved by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExecuteModuleActionUiEvent.Completed -> {
                    successful = event.successful
                    onCompleted()
                }

                is ExecuteModuleActionUiEvent.LogSaved -> logSaved = true

                is ExecuteModuleActionUiEvent.Error -> saveFailed = true
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
                    true -> Icons.TwoTone.CheckCircle
                    false -> Icons.TwoTone.Error
                    null -> Icons.TwoTone.PlayArrow
                },
            )
        }
        item {
            WearActionButton(
                spec,
                Icons.AutoMirrored.TwoTone.Article,
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
                Icons.TwoTone.Save,
                stringResource(R.string.wear_save_output),
                { viewModel.dispatch(ExecuteModuleActionUiAction.SaveLog) },
                enabled = !state.running,
                secondaryText = stringResource(R.string.wear_save_output_summary),
            )
        }
    }
    val savedText = stringResource(R.string.log_saved)
    val failedText = stringResource(R.string.operation_failed)
    val style = ConfirmationDialogDefaults.curvedTextStyle
    SuccessConfirmationDialog(
        visible = logSaved,
        onDismissRequest = { logSaved = false },
        curvedText = { confirmationDialogCurvedText(savedText, style) },
    )
    FailureConfirmationDialog(
        visible = saveFailed,
        onDismissRequest = { saveFailed = false },
        curvedText = { confirmationDialogCurvedText(failedText, style) },
    )
}

/**
 * Confirms installing a module ZIP, as the phone does before flashing: the file and its
 * `module.prop` details, the target device, then [onConfirm] starts the installation. Missing
 * `module.prop` fields are left out, and an unreadable one falls back to the file name.
 */
@Composable
internal fun WearModuleInstallConfirmPage(uri: String, onBack: () -> Unit, onConfirm: () -> Unit) {
    val context = LocalContext.current
    val zipDetector = koinInject<ZipFileDetector>()
    var info by remember { mutableStateOf<ZipFileInfo?>(null) }
    LaunchedEffect(uri) {
        info = withContext(Dispatchers.IO) { zipDetector.parseModuleInfo(context, uri.toUri()) }
    }
    val fileName = uri.toUri().lastPathSegment?.substringAfterLast('/').orEmpty()
    val module = info
    WearList(isLoading = module == null, onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.confirm_installation)) }
        item { WearSectionHeader(spec, stringResource(R.string.wear_file_check)) }
        if (module != null) {
            val version = listOfNotNull(
                module.version.ifBlank { null },
                module.versionCode.ifBlank { null }?.let { "($it)" },
            ).joinToString(" ")
            item {
                WearChip(
                    spec,
                    module.name.ifBlank { fileName.ifBlank { stringResource(R.string.unknown_module) } },
                    secondaryLabel = listOfNotNull(
                        version.ifBlank { null }?.let { stringResource(R.string.wear_labeled_value, stringResource(R.string.module_version), it) },
                        module.author.ifBlank { null }?.let { stringResource(R.string.wear_labeled_value, stringResource(R.string.module_author), it) },
                        fileName.ifBlank { null }?.let { stringResource(R.string.wear_labeled_value, stringResource(R.string.wear_file_name), it) },
                    ).joinToString("\n").ifBlank { null },
                    icon = Icons.TwoTone.Folder,
                )
            }
            if (module.description.isNotBlank()) item { WearInfoCard(spec) { Text(module.description) } }
        }
        item {
            WearChip(
                spec,
                stringResource(R.string.wear_install_target),
                secondaryLabel = Build.MODEL,
                icon = Icons.TwoTone.Smartphone,
            )
        }
        item { WearSectionHeader(spec, stringResource(R.string.wear_operations)) }
        item {
            WearActionButton(
                spec,
                Icons.TwoTone.Download,
                stringResource(R.string.confirm_installation),
                onConfirm,
                secondaryText = stringResource(R.string.wear_start_install_module),
                colors = ButtonDefaults.buttonColors(),
            )
        }
    }
}
