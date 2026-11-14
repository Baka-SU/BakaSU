package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import org.bakasu.bakasu.R

/** Local opening failures use the transfer dialog's appearance with an explicit watch target. */
@Composable
internal fun WearWatchOpenFailureDialog(message: String?, onDismiss: () -> Unit) {
    var shown by remember { mutableStateOf(message.orEmpty()) }
    if (message != null) shown = message
    val style = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = message != null,
        onDismissRequest = onDismiss,
        curvedText = { openOnPhoneDialogCurvedText(shown, style) },
        colors = OpenOnPhoneDialogDefaults.colors(
            iconColor = MaterialTheme.colorScheme.onErrorContainer,
            iconContainerColor = MaterialTheme.colorScheme.errorContainer,
            progressIndicatorColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        Icon(Icons.Rounded.Watch, stringResource(R.string.wear_watch_webui), Modifier.size(48.dp))
    }
}
