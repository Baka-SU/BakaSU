package org.bakasu.bakasu.ui.wear.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Dialog
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.component.DialogHandle
import org.bakasu.bakasu.ui.component.rememberCustomDialog

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * A confirmation as a full-screen Wear dialog list: the operation title, its full [message] to
 * check before confirming, then Confirm and Cancel. Swiping away also cancels.
 */
@Composable
fun rememberWearConfirmDialog(title: String, message: String, onConfirm: () -> Unit): DialogHandle = rememberCustomDialog { dismiss ->
    Dialog(visible = true, onDismissRequest = dismiss) {
        WearList { spec ->
            item { WearPageHeader(spec, title) }
            item { WearSectionHeader(spec, stringResource(R.string.wear_operation_scope)) }
            if (message.isNotBlank()) {
                item { WearStatusItem(spec, Icons.TwoTone.Warning, message, centered = false) }
            }
            item {
                WearActionButton(
                    spec,
                    Icons.TwoTone.CheckCircle,
                    stringResource(R.string.confirm),
                    {
                        dismiss()
                        onConfirm()
                    },
                    secondaryText = stringResource(R.string.wear_confirm_summary),
                    colors = ButtonDefaults.buttonColors(),
                )
            }
            item {
                WearActionButton(
                    spec,
                    Icons.TwoTone.Close,
                    stringResource(R.string.cancel),
                    dismiss,
                    secondaryText = stringResource(R.string.wear_cancel_summary),
                )
            }
        }
    }
}
