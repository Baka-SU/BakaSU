package org.bakasu.bakasu.ui.wear.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
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
        WearNoticeDialog(
            visible = true,
            onDismissRequest = close,
            icon = { WearDialogIcon(Icons.Rounded.Error, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.operation_failed)) },
            text = { Text(message.orEmpty()) },
        )
    }
    LaunchedEffect(message) {
        if (message != null) dialog.show() else dialog.hide()
    }
}
