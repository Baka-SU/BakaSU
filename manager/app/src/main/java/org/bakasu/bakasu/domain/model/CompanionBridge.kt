package org.bakasu.bakasu.domain.model

import androidx.annotation.StringRes
import org.bakasu.bakasu.R

enum class BridgeFailure(@param:StringRes val resId: Int) {
    UNSUPPORTED(R.string.wear_bridge_unsupported),
    PERMISSION(R.string.wear_bridge_permission_required),
    BLUETOOTH_DISABLED(R.string.wear_bridge_bluetooth_disabled),
    SETUP_REQUIRED(R.string.wear_bridge_setup_required),
    CONNECTION(R.string.wear_bridge_connect_failed),
    PAIRING(R.string.wear_bridge_pair_failed),
    ASSOCIATION(R.string.wear_bridge_association_unsupported),
    NOTIFICATIONS(R.string.wear_bridge_notification_required),
}

class BridgeException(val reason: BridgeFailure, cause: Throwable? = null) : Exception(reason.name, cause)
data class BridgePeer(val address: String, val name: String)
data class BridgeState(val peer: BridgePeer? = null, val paired: Boolean = false, val enabled: Boolean = false, val pairingCode: String? = null)
