package org.bakasu.bakasu.ui.wear.component

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText

/** Shared presentation; callers retain their state during exit and own cancellation semantics. */
@Composable
internal fun WearDeviceFeedbackDialog(
    visible: Boolean,
    message: String,
    failed: Boolean,
    onDismiss: () -> Unit,
    durationMillis: Long = OpenOnPhoneDialogDefaults.DurationMillis,
    icon: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val style = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        curvedText = { openOnPhoneDialogCurvedText(message, style) },
        durationMillis = durationMillis,
        colors = OpenOnPhoneDialogDefaults.colors(
            iconColor = if (failed) colors.onErrorContainer else colors.onPrimaryContainer,
            iconContainerColor = if (failed) colors.errorContainer else colors.primaryContainer,
            progressIndicatorColor = if (failed) colors.error else colors.primary,
        ),
    ) {
        icon()
    }
}
