package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.bakasu.bakasu.domain.model.BridgeStage
import org.bakasu.bakasu.domain.model.BridgeTransfer

@Composable
fun WearPhoneTransferDialog(transfer: BridgeTransfer?, onCancel: () -> Unit, onDismiss: () -> Unit) {
    var shown by remember { mutableStateOf(transfer) }
    if (transfer != null) shown = transfer
    val state = shown
    val waiting = state?.stage in setOf(BridgeStage.CHECKING, BridgeStage.CONNECTING, BridgeStage.WAITING)
    val failed = state?.stage == BridgeStage.FAILED
    val text = when (state?.stage) {
        BridgeStage.CHECKING -> stringResource(R.string.wear_phone_checking)

        BridgeStage.CONNECTING -> stringResource(R.string.wear_bridge_connecting)

        BridgeStage.WAITING -> stringResource(R.string.wear_bridge_confirming)

        BridgeStage.FAILED -> stringResource(
            when (state.failure) {
                BridgeFailure.PERMISSION -> R.string.wear_bridge_permission_short
                BridgeFailure.BLUETOOTH_DISABLED -> R.string.wear_bridge_bluetooth_short
                BridgeFailure.NOT_PAIRED -> R.string.wear_bridge_pair_short
                BridgeFailure.NOT_CONNECTED -> R.string.wear_phone_not_connected
                BridgeFailure.REJECTED -> R.string.wear_bridge_rejected
                BridgeFailure.NOTIFICATIONS -> R.string.wear_bridge_notification_short
                BridgeFailure.OPEN_FAILED -> R.string.wear_link_open_failed
                BridgeFailure.RESULT_UNKNOWN -> R.string.wear_phone_result_unknown
                else -> R.string.wear_phone_connection_failed
            },
        )

        else -> OpenOnPhoneDialogDefaults.text
    }
    WearDeviceFeedbackDialog(
        visible = transfer != null,
        onDismiss = { if (waiting) onCancel() else onDismiss() },
        message = text,
        failed = failed,
        durationMillis = if (waiting) 300_000L else OpenOnPhoneDialogDefaults.DurationMillis,
    ) {
        when {
            state?.stage == BridgeStage.CONNECTING || state?.stage == BridgeStage.CHECKING -> WearLoadingIndicator()

            state?.stage == BridgeStage.OPENED -> OpenOnPhoneDialogDefaults.Icon()

            else -> Icon(
                when {
                    state?.stage == BridgeStage.WAITING -> Icons.Rounded.PhoneAndroid
                    state?.failure == BridgeFailure.PERMISSION -> Icons.Rounded.Lock
                    state?.failure == BridgeFailure.REJECTED -> Icons.Rounded.Close
                    state?.failure == BridgeFailure.BLUETOOTH_DISABLED -> Icons.Rounded.BluetoothDisabled
                    state?.failure == BridgeFailure.NOTIFICATIONS -> Icons.Rounded.NotificationsOff
                    else -> Icons.Rounded.LinkOff
                },
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}
