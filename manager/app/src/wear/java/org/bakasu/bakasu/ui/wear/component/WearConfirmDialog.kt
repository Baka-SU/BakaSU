package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

@Composable
fun wearEmphasizedBodyStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)

/** Confirms after dismissing the dialog; Cancel, Back and swipe-dismiss only close it. */
@Composable
fun rememberWearConfirmDialog(
    title: String,
    message: String,
    destructive: Boolean = false,
    subject: String? = null,
    icon: ImageVector = if (destructive) Icons.Rounded.Warning else Icons.Rounded.Info,
    onConfirm: () -> Unit,
): DialogHandle = rememberCustomDialog { dismiss ->
    val dialogIcon: @Composable () -> Unit = {
        WearDialogIcon(
            icon,
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
            verticalArrangement = WearDialogVerticalArrangement,
            contentPadding = wearDialogContentPadding(),
            visible = true,
            onDismissRequest = dismiss,
            icon = dialogIcon,
            title = { Text(title) },
            text = message.takeIf { it.isNotBlank() }?.let { { Text(it) } },
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )
    } else {
        AlertDialog(
            verticalArrangement = WearDialogVerticalArrangement,
            contentPadding = wearDialogContentPadding(),
            visible = true,
            onDismissRequest = dismiss,
            icon = dialogIcon,
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
