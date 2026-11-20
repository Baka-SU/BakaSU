package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.ui.component.rememberCustomDialog

/** A message owns visibility; all dismiss paths also clear the caller's feedback state. */
@Composable
internal fun WearMessageDialog(
    message: String?,
    title: String,
    onDismiss: () -> Unit,
    icon: @Composable (() -> Unit)? = null,
) {
    val dialog = rememberCustomDialog { dismiss ->
        WearNoticeDialog(
            visible = true,
            onDismissRequest = {
                dismiss()
                onDismiss()
            },
            title = { Text(title) },
            icon = icon,
            text = { Text(message.orEmpty()) },
        )
    }
    LaunchedEffect(message) {
        if (message != null) dialog.show() else dialog.hide()
    }
}

/** Single-action notices use a scrolling circular footer on square screens. */
@Composable
internal fun WearNoticeDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    icon: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    if (LocalConfiguration.current.isScreenRound) {
        AlertDialog(
            verticalArrangement = WearDialogVerticalArrangement,
            visible = visible,
            onDismissRequest = onDismissRequest,
            title = title,
            icon = icon,
            text = text,
            contentPadding = wearDialogContentPadding(edgeButton = true),
            edgeButton = { AlertDialogDefaults.EdgeButton(onClick = onDismissRequest) },
        )
    } else {
        val layoutDirection = LocalLayoutDirection.current
        AlertDialog(
            verticalArrangement = WearDialogVerticalArrangement,
            visible = visible,
            onDismissRequest = onDismissRequest,
            title = title,
            icon = icon,
            text = text,
            transformationSpec = rememberWearTransformationSpec(),
            contentPadding = { isScrollable ->
                val defaults = if (icon != null) {
                    AlertDialogDefaults.buttonStackWithIconContentPadding(isScrollable)
                } else {
                    AlertDialogDefaults.buttonStackContentPadding(isScrollable)
                }
                PaddingValues(
                    start = defaults.calculateStartPadding(layoutDirection),
                    top = AlertDialogDefaults.contentPadding().calculateTopPadding(),
                    end = defaults.calculateEndPadding(layoutDirection),
                    // The circular footer needs no round-screen space below the final item.
                    bottom = 8.dp,
                )
            },
        ) {
            item { WearPageFooterButton(onClick = onDismissRequest) }
        }
    }
}
