package org.bakasu.bakasu.ui.wear.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.RepositoryError
import org.bakasu.bakasu.domain.model.RepositoryFailure
import org.bakasu.bakasu.ui.component.rememberCustomDialog

@Composable
internal fun WearRepositoryConnectionDialog(failure: RepositoryFailure?, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val dialog = rememberCustomDialog { dismiss ->
        val close = {
            dismiss()
            onDismiss()
        }
        AlertDialog(
            verticalArrangement = WearDialogVerticalArrangement,
            contentPadding = wearDialogContentPadding(),
            visible = true,
            onDismissRequest = close,
            icon = { WearDialogIcon(Icons.Rounded.Error, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.wear_repo_connect_failed)) },
            text = { Text(stringResource(if (failure?.reason == RepositoryError.OFFLINE) R.string.wear_no_internet else R.string.wear_repo_unreachable)) },
            confirmButton = {
                AlertDialogDefaults.ConfirmButton(
                    // Clearing the failure hides the dialog; an immediate retry failure stays visible.
                    onClick = onRetry,
                )
            },
            dismissButton = { AlertDialogDefaults.DismissButton(onClick = close) },
        )
    }
    LaunchedEffect(failure) {
        if (failure != null) dialog.show() else dialog.hide()
    }
}
