package org.bakasu.bakasu.ui.wear.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.component.rememberCustomDialog

/** One result, acknowledged in the same scrolling dialog as a Home warning. */
data class WearResult(val message: String, val success: Boolean, val brief: Boolean = false)

@Composable
fun WearResultDialog(result: WearResult?, onDismiss: () -> Unit) {
    val dialog = rememberCustomDialog { dismiss ->
        val close = {
            dismiss()
            onDismiss()
        }
        AlertDialog(
            visible = true,
            onDismissRequest = close,
            icon = {
                WearDialogIcon(
                    if (result?.success == true) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                    tint = if (result?.success == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(if (result?.brief == true) result.message else stringResource(if (result?.success == true) R.string.wear_complete_status else R.string.operation_failed)) },
            text = if (result?.brief == true) {
                null
            } else {
                { Text(result?.message.orEmpty()) }
            },
            edgeButton = { AlertDialogDefaults.EdgeButton(onClick = close) },
        )
    }
    LaunchedEffect(result) {
        if (result != null) dialog.show() else dialog.hide()
    }
}
