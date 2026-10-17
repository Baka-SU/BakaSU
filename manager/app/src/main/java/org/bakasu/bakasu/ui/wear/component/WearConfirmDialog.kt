package org.bakasu.bakasu.ui.wear.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.ui.component.DialogHandle
import org.bakasu.bakasu.ui.component.rememberCustomDialog

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * A confirmation as the official Wear [AlertDialog]: the operation [title], its full [message] to
 * check before confirming (it scrolls when long), then Confirm and Cancel. Cancel, swiping away and
 * Back only close it; [onConfirm] runs once, after the dialog has closed. A [destructive] operation,
 * such as deleting or uninstalling, takes the error colors on its icon and Confirm button.
 */
@Composable
fun rememberWearConfirmDialog(
    title: String,
    message: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
): DialogHandle = rememberCustomDialog { dismiss ->
    AlertDialog(
        visible = true,
        onDismissRequest = dismiss,
        icon = {
            Icon(
                if (destructive) Icons.TwoTone.Warning else Icons.TwoTone.Info,
                contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(title) },
        text = message.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    // Closing first keeps a second tap from confirming again.
                    dismiss()
                    onConfirm()
                },
                colors = if (destructive) {
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else {
                    IconButtonDefaults.filledIconButtonColors()
                },
            )
        },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = dismiss) },
    )
}
