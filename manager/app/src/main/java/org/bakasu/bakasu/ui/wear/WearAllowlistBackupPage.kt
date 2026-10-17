package org.bakasu.bakasu.ui.wear

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.confirmationDialogCurvedText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.AllowlistOperationResult
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiAction
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiEvent
import org.bakasu.bakasu.ui.viewmodel.SuperUserViewModel
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.rememberWearConfirmDialog

/**
 * Backing up and restoring the allowlist with the phone's SuperUserViewModel actions: the backup
 * is written to a document the user picks, and restoring asks first because it overwrites the
 * current allowlist.
 */
@Composable
internal fun WearAllowlistBackupPage(viewModel: SuperUserViewModel, onBack: () -> Unit) {
    var restoreUri by rememberSaveable { mutableStateOf("") }
    var successText by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val backupSuccess = stringResource(R.string.allowlist_backup_success)
    val restoreSuccess = stringResource(R.string.allowlist_restore_success)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is SuperUserUiEvent.AllowlistOperationFinished) {
                if (event.result == AllowlistOperationResult.Success) {
                    successText = if (event.restore) restoreSuccess else backupSuccess
                } else {
                    failed = true
                }
            }
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.dispatch(SuperUserUiAction.BackupAllowlist(uri.toString()))
    }
    val restoreDialog = rememberWearConfirmDialog(
        stringResource(R.string.allowlist_restore_confirm_title),
        stringResource(R.string.allowlist_restore_confirm_message),
    ) { viewModel.dispatch(SuperUserUiAction.RestoreAllowlist(restoreUri)) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            restoreUri = uri.toString()
            restoreDialog.show()
        }
    }

    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_allowlist_backup)) }
        item { WearSectionHeader(spec, stringResource(R.string.wear_allowlist)) }
        item {
            WearActionButton(
                spec,
                R.drawable.wear_symbol_save,
                stringResource(R.string.backup_allowlist),
                {
                    // The same file name as the phone backup, so both are recognized alike.
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    runCatching { backupLauncher.launch("ksu_allowlist_backup_$timestamp.dat") }.onFailure { failed = true }
                },
                secondaryText = stringResource(R.string.wear_backup_allowlist_summary),
            )
        }
        item {
            WearActionButton(
                spec,
                R.drawable.wear_symbol_history,
                stringResource(R.string.restore_allowlist),
                { runCatching { restoreLauncher.launch(arrayOf("application/octet-stream")) }.onFailure { failed = true } },
                secondaryText = stringResource(R.string.wear_restore_allowlist_summary),
            )
        }
    }
    val failedText = stringResource(R.string.operation_failed)
    val style = ConfirmationDialogDefaults.curvedTextStyle
    SuccessConfirmationDialog(
        visible = successText != null,
        onDismissRequest = { successText = null },
        curvedText = { confirmationDialogCurvedText(successText.orEmpty(), style) },
    )
    FailureConfirmationDialog(
        visible = failed,
        onDismissRequest = { failed = false },
        curvedText = { confirmationDialogCurvedText(failedText, style) },
    )
}
