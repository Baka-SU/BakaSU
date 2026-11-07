package org.bakasu.bakasu.domain.model

import androidx.annotation.StringRes
import org.bakasu.bakasu.R

enum class BridgeFailure(@param:StringRes val resId: Int) {
    UNSUPPORTED(R.string.wear_bridge_unsupported),
    PERMISSION(R.string.wear_bridge_permission_required),
    BLUETOOTH_DISABLED(R.string.wear_bridge_bluetooth_disabled),
    SETUP_REQUIRED(R.string.wear_bridge_setup_required),
    CONNECTION(R.string.wear_bridge_connect_failed),
    ASSOCIATION(R.string.wear_bridge_association_unsupported),
    NOTIFICATIONS(R.string.wear_bridge_notification_required),
    NOT_PAIRED(R.string.wear_bridge_not_paired),
    REJECTED(R.string.wear_bridge_rejected),
}

class BridgeException(val reason: BridgeFailure, cause: Throwable? = null) : Exception(reason.name, cause)
data class BridgePeer(val address: String, val name: String)
data class BridgeState(val peer: BridgePeer? = null, val enabled: Boolean = false, val connected: Boolean = false, val remembered: Boolean = false)

enum class BridgeAction { WEB_UI, LINK }

enum class BridgeStage { CONNECTING, WAITING, OPENED, FAILED }

data class BridgeTransfer(val stage: BridgeStage, val failure: BridgeFailure? = null)

data class BridgeRequest(
    val id: String,
    val peer: BridgePeer,
    val action: BridgeAction,
    val value: String,
    val title: String,
    val requiresConfirmation: Boolean,
    val readyToOpen: Boolean = false,
)
