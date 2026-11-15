package org.bakasu.bakasu.ui.wear.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R

/** Blocking failures stay readable until dismissed, rather than replacing the page with a card. */
@Composable
fun WearErrorDialog(message: String?, onDismiss: () -> Unit = {}) {
    WearMessageDialog(
        message = message,
        title = stringResource(R.string.operation_failed),
        onDismiss = onDismiss,
        icon = { WearDialogIcon(Icons.Rounded.Error, tint = MaterialTheme.colorScheme.error) },
    )
}
