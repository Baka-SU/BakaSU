package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/** The large body style in SemiBold, for what a dialog acts on, such as a module name. */
@Composable
fun wearEmphasizedBodyStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)

/**
 * A confirmation as the official Wear [AlertDialog]: the operation [title], then Confirm and Cancel.
 * Without a [subject] the [message] sits in the dialog's text slot. With one, the subject (such as
 * the module to uninstall) takes the text slot in the emphasized body style, and the message follows
 * in the body style, centered across the content width like the slots above; it scrolls when long.
 * Cancel, swiping away and Back only close it; [onConfirm] runs once, after the dialog has closed. A
 * [destructive] operation, such as deleting or uninstalling, takes the error colors on its icon and
 * Confirm button.
 */
@Composable
fun rememberWearConfirmDialog(
    title: String,
    message: String,
    destructive: Boolean = false,
    subject: String? = null,
    onConfirm: () -> Unit,
): DialogHandle = rememberCustomDialog { dismiss ->
    val icon: @Composable () -> Unit = {
        WearDialogIcon(
            if (destructive) Icons.Rounded.Warning else Icons.Rounded.Info,
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
    val confirmButton: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
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
    }
    val dismissButton: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        AlertDialogDefaults.DismissButton(onClick = dismiss)
    }
    if (subject == null) {
        AlertDialog(
            visible = true,
            onDismissRequest = dismiss,
            icon = icon,
            title = { Text(title) },
            text = message.takeIf { it.isNotBlank() }?.let { { Text(it) } },
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )
    } else {
        AlertDialog(
            visible = true,
            onDismissRequest = dismiss,
            icon = icon,
            title = { Text(title) },
            text = { Text(subject, style = wearEmphasizedBodyStyle()) },
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        ) {
            if (message.isNotBlank()) {
                item {
                    Text(
                        message,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
