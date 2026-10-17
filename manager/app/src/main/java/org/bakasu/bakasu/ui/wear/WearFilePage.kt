package org.bakasu.bakasu.ui.wear

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.file.matchesMimeType
import org.bakasu.bakasu.data.file.wearFileMimeType
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.util.ActivityResumeEffect
import org.bakasu.bakasu.ui.viewmodel.WearFileEvent
import org.bakasu.bakasu.ui.viewmodel.WearFileViewModel
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearChip
import org.bakasu.bakasu.ui.wear.component.WearChipEmphasis
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearScaledItem
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone
import org.bakasu.bakasu.ui.wear.component.WearSubPage
import org.bakasu.bakasu.ui.wear.component.WearTextInputPage
import org.bakasu.bakasu.ui.wear.component.wearGroupGap
import org.koin.compose.viewmodel.koinViewModel

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * The built-in picker as a Wear list: the title, the current path as a caption, a low-emphasis
 * entry to the parent folder, then folders and the files matching [mimeTypes] as tonal buttons. In
 * save mode the file name follows and the edge button confirms; otherwise picking a file returns it.
 * [onPicked] receives the file as a content URI. Failed operations open an AlertDialog to retry or
 * cancel, and browsing without root first asks for the storage permission.
 */
@Composable
internal fun WearFilePage(
    mimeTypes: List<String>,
    saving: Boolean,
    initialName: String,
    onBack: () -> Unit,
    onPicked: (Uri) -> Unit,
) {
    val viewModel = koinViewModel<WearFileViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is WearFileEvent.Picked) onPicked(event.uri.toUri())
        }
    }
    var editingName by rememberSaveable { mutableStateOf(false) }
    // The last directory or file operation, so a failure can be retried as it was requested.
    var lastOperation by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun perform(operation: () -> Unit) {
        lastOperation = operation
        operation()
    }
    LaunchedEffect(viewModel) {
        if (viewModel.state.value.directory == null) {
            viewModel.setName(initialName)
            perform { viewModel.load(null, mimeTypes, saving) }
        }
    }
    // A failed operation needs a decision, so it opens an AlertDialog with Retry and Cancel instead of
    // a message in the list. Cancelling a failed first load leaves the picker, as nothing can be shown.
    val errorDialog = rememberCustomDialog { dismiss ->
        val close = {
            dismiss()
            viewModel.clearError()
            if (state.directory == null) onBack()
        }
        AlertDialog(
            visible = true,
            onDismissRequest = close,
            icon = { Icon(painterResource(R.drawable.wear_symbol_error_fill1), contentDescription = null) },
            title = { Text(stringResource(R.string.wear_file_access_failed)) },
            text = { Text(state.error ?: stringResource(R.string.operation_failed)) },
            confirmButton = {
                AlertDialogDefaults.ConfirmButton(
                    onClick = {
                        dismiss()
                        viewModel.clearError()
                        lastOperation?.invoke()
                    },
                ) { Icon(painterResource(R.drawable.wear_symbol_refresh), contentDescription = stringResource(R.string.wear_retry)) }
            },
            dismissButton = { AlertDialogDefaults.DismissButton(onClick = close) },
        )
    }
    // Without root, shared storage needs a permission the user grants in the system; the picker asks
    // once per visit and reloads when the user returns from granting it.
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    var awaitingPermission by rememberSaveable { mutableStateOf(false) }
    var permissionFailed by remember { mutableStateOf(false) }
    fun reload() = perform { viewModel.load(state.directory?.path, mimeTypes, saving) }
    val readPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reload() }
    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val app = "package:${context.packageName}".toUri()
            // Not every watch has the all-files access screen, so fall back to the general list and app info.
            val opened = listOf(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, app),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app),
            ).any { runCatching { context.startActivity(it) }.isSuccess }
            if (opened) awaitingPermission = true else permissionFailed = true
        } else {
            readPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    ActivityResumeEffect {
        if (awaitingPermission) {
            awaitingPermission = false
            reload()
        }
    }
    val permissionDialog = rememberCustomDialog { dismiss ->
        val cancel = {
            dismiss()
            if (state.failed) errorDialog.show()
        }
        AlertDialog(
            visible = true,
            onDismissRequest = cancel,
            icon = { Icon(painterResource(R.drawable.wear_symbol_folder), contentDescription = null) },
            title = { Text(stringResource(R.string.wear_storage_permission_title)) },
            text = { Text(stringResource(R.string.wear_storage_permission_message)) },
            confirmButton = {
                AlertDialogDefaults.ConfirmButton(
                    onClick = {
                        dismiss()
                        requestStoragePermission()
                    },
                )
            },
            dismissButton = { AlertDialogDefaults.DismissButton(onClick = cancel) },
        )
    }
    // One dialog at a time: the permission request comes first, as granting it may resolve the failure.
    LaunchedEffect(state.failed, state.storagePermissionRequired) {
        if (state.storagePermissionRequired && !permissionAsked) {
            permissionAsked = true
            permissionDialog.show()
        } else if (state.failed && !permissionDialog.isShown) {
            errorDialog.show()
        }
    }
    val failedText = stringResource(R.string.operation_failed)
    val failedStyle = ConfirmationDialogDefaults.curvedTextStyle
    FailureConfirmationDialog(
        visible = permissionFailed,
        onDismissRequest = { permissionFailed = false },
        curvedText = { confirmationDialogCurvedText(failedText, failedStyle) },
    )
    if (editingName) {
        WearSubPage({ editingName = false }) {
            WearTextInputPage(stringResource(R.string.wear_file_name), state.name) {
                viewModel.setName(it)
                editingName = false
            }
        }
    } else {
        val directory = state.directory
        WearList(
            isLoading = state.loading || directory == null && !state.failed,
            onBack = onBack,
            snap = true,
            onConfirm = if (saving && directory?.writable == true) ({ perform { viewModel.create() } }) else null,
        ) { spec ->
            item { WearPageHeader(spec, stringResource(R.string.wear_picker_mode)) }
            directory?.let {
                item {
                    WearScaledItem(spec) {
                        Text(
                            it.path,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyExtraSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.StartEllipsis,
                        )
                    }
                }
            }
            directory?.parent?.let { parent ->
                item {
                    WearChip(
                        spec,
                        stringResource(R.string.wear_parent_directory),
                        icon = R.drawable.wear_symbol_subdirectory_arrow_left,
                        emphasis = WearChipEmphasis.HIGH,
                        onClick = { perform { viewModel.load(parent, mimeTypes, saving) } },
                    )
                }
            }
            if (directory != null && directory.entries.isEmpty()) {
                item {
                    WearStatusItem(spec, R.drawable.wear_symbol_folder_off, stringResource(R.string.wear_directory_empty))
                }
            }
            items(directory?.entries.orEmpty(), key = { it.path }) { entry ->
                val mimeType = wearFileMimeType(entry.name)
                WearActionButton(
                    spec,
                    icon = when {
                        entry.directory -> R.drawable.wear_symbol_folder
                        matchesMimeType(mimeType, listOf("image/*")) -> R.drawable.wear_symbol_image
                        mimeType == "application/zip" -> R.drawable.wear_symbol_folder_zip
                        else -> R.drawable.wear_symbol_file_present
                    },
                    label = entry.name,
                    onClick = {
                        perform { if (entry.directory) viewModel.load(entry.path, mimeTypes, saving) else viewModel.select(entry.path) }
                    },
                    secondaryText = if (entry.directory) null else Formatter.formatShortFileSize(context, entry.size),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                )
            }
            if (saving) {
                wearGroupGap("save-gap")
                if (directory?.writable == false) {
                    item {
                        WearStatusItem(spec, R.drawable.wear_symbol_error_fill1, stringResource(R.string.wear_directory_unavailable), tone = WearStatusTone.ERROR)
                    }
                }
                item {
                    WearActionButton(
                        spec,
                        R.drawable.wear_symbol_drive_file_rename_outline,
                        state.name,
                        { editingName = true },
                        secondaryText = stringResource(R.string.wear_file_name),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                    )
                }
            }
        }
    }
}
