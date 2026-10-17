package org.bakasu.bakasu.ui.wear

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.wear.compose.material3.lazy.transformedHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.file.ZipFileDetector
import org.bakasu.bakasu.data.file.ZipFileInfo
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.domain.model.ModuleUpdateMetadata
import org.bakasu.bakasu.domain.usecase.FetchRemoteTextUseCase
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
import org.bakasu.bakasu.ui.wear.component.wearEmphasizedBodyStyle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A module update with the phone's use cases: the changelog is read first, then [onDownload]
 * downloads the ZIP on the shared module download page, which installs it.
 */
@Composable
internal fun WearModuleUpdatePage(module: InstalledModule?, onBack: () -> Unit, onDownload: (InstalledModule, ModuleUpdateMetadata) -> Unit) {
    val fetchRemoteText = koinInject<FetchRemoteTextUseCase>()
    var changelog by rememberSaveable { mutableStateOf<String?>(null) }
    var changelogError by rememberSaveable { mutableStateOf<String?>(null) }
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
                        R.drawable.wear_symbol_error_fill1,
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
            // Like the phone, the update is offered only after its changelog has been read; the
            // download itself runs on the shared module download page.
            item {
                Button(
                    onClick = { onDownload(module, update) },
                    enabled = changelog != null,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                    transformation = SurfaceTransformation(spec),
                    icon = { Icon(painterResource(R.drawable.wear_symbol_download), contentDescription = null) },
                    secondaryLabel = {
                        Text(
                            when {
                                changelogError != null -> stringResource(R.string.wear_update_changelog_unavailable)
                                changelog == null -> stringResource(R.string.wear_update_reading_changelog)
                                else -> stringResource(R.string.wear_update_to, update.version)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                ) {
                    Text(stringResource(R.string.wear_update_download), maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                    true -> R.drawable.wear_symbol_check_circle_fill1
                    false -> R.drawable.wear_symbol_error_fill1
                    null -> R.drawable.wear_symbol_play_arrow
                },
            )
        }
        item {
            WearActionButton(
                spec,
                R.drawable.wear_symbol_article,
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
                R.drawable.wear_symbol_save,
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
 * Confirms installing a module ZIP with the official Wear [AlertDialog], as the phone does before
 * flashing. The type follows the Wear roles: the dialog title in the title slot's own style, the
 * module name in an emphasized body style (wrapping, never cut), the metadata in quieter body styles
 * and the description in the body text style, start-aligned for reading. Missing `module.prop`
 * fields are left out, and an unreadable one falls back to the file name. Only Confirm calls
 * [onConfirm]; Cancel, swiping away and Back call [onDismiss]. Nothing shows while [uri] is null or
 * its details are still read.
 */
@Composable
internal fun WearModuleInstallConfirmDialog(uri: String?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val context = LocalContext.current
    val zipDetector = koinInject<ZipFileDetector>()
    var info by remember { mutableStateOf<ZipFileInfo?>(null) }
    LaunchedEffect(uri) {
        info = null
        if (uri != null) info = withContext(Dispatchers.IO) { zipDetector.parseModuleInfo(context, uri.toUri()) }
    }
    val module = info
    val fileName = uri?.toUri()?.lastPathSegment?.substringAfterLast('/').orEmpty()
    val version = listOfNotNull(
        module?.version?.ifBlank { null },
        module?.versionCode?.ifBlank { null }?.let { "($it)" },
    ).joinToString(" ")
    AlertDialog(
        visible = uri != null && module != null,
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painterResource(R.drawable.wear_symbol_extension),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.confirm_installation)) },
        text = {
            Text(
                module?.name?.ifBlank { null } ?: fileName.ifBlank { stringResource(R.string.unknown_module) },
                style = wearEmphasizedBodyStyle(),
            )
        },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    // Closing first keeps a second tap from starting a second installation.
                    val target = uri ?: return@ConfirmButton
                    onDismiss()
                    onConfirm(target)
                },
            )
        },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = onDismiss) },
    ) {
        // The metadata, one field per item at the list's 4dp spacing.
        if (version.isNotBlank()) item { WearDialogField(stringResource(R.string.module_version), version) }
        module?.author?.ifBlank { null }?.let { author -> item { WearDialogField(stringResource(R.string.module_author), author) } }
        if (fileName.isNotBlank()) item { WearDialogField(stringResource(R.string.wear_file_name), fileName) }
        item { WearDialogField(stringResource(R.string.wear_install_target), Build.MODEL) }
        // The description is its own group: 4dp more above it gives the dialog's 8dp group spacing.
        module?.description?.ifBlank { null }?.let { description ->
            item {
                Text(
                    description,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

/** One metadata field of a dialog: a quiet small label above its value, both start-aligned. */
@Composable
private fun WearDialogField(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
    }
}
