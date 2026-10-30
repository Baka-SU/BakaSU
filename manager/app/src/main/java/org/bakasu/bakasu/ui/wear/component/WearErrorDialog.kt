package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.component.rememberCustomDialog

/** Blocking failures stay readable until dismissed, rather than replacing the page with a card. */
@Composable
fun WearErrorDialog(message: String?, onDismiss: () -> Unit = {}) {
    val dialog = rememberCustomDialog { dismiss ->
        val close = {
            dismiss()
            onDismiss()
        }
        AlertDialog(
            visible = true,
            onDismissRequest = close,
            icon = {
                Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(ButtonDefaults.LargeIconSize), tint = MaterialTheme.colorScheme.error)
            },
            title = { Text(stringResource(R.string.operation_failed)) },
            text = { Text(message.orEmpty()) },
            edgeButton = { AlertDialogDefaults.EdgeButton(onClick = close) },
        )
    }
    LaunchedEffect(message) {
        if (message != null) dialog.show() else dialog.hide()
    }
}
