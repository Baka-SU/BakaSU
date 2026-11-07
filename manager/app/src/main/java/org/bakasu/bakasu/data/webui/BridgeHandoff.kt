package org.bakasu.bakasu.data.webui

import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bakasu.bakasu.domain.model.BridgeAction
import org.bakasu.bakasu.domain.model.BridgeRequest

/** One phone confirmation, kept alive only for its requesting Bluetooth connection. */
internal class PendingBridgeRequest(val request: BridgeRequest) {
    val approval = CompletableDeferred<Boolean?>()
    val opened = CompletableDeferred<Boolean>()
    val presented = CompletableDeferred<Unit>()
}

/** Retains one operation across Android's permission dialog and ignores a late result after cancel. */
internal class BridgePermissionRequest {
    private val mutableId = MutableStateFlow<String?>(null)
    val id = mutableId.asStateFlow()
    private var answer: CompletableDeferred<Boolean>? = null

    suspend fun await(): Boolean {
        val pending = CompletableDeferred<Boolean>()
        answer = pending
        mutableId.value = UUID.randomUUID().toString()
        return try {
            pending.await()
        } finally {
            if (answer === pending) {
                answer = null
                mutableId.value = null
            }
        }
    }

    fun answer(id: String, granted: Boolean) {
        if (mutableId.value == id) {
            mutableId.value = null
            answer?.complete(granted)
        }
    }

    fun cancel() {
        mutableId.value = null
        answer?.cancel()
        answer = null
    }
}

internal object BridgeHandoff {
    const val OPERATION = "handoff"
    const val CONFIRMATION_TIMEOUT_MS = 180_000L

    fun valid(action: BridgeAction, value: String): Boolean = when (action) {
        BridgeAction.LINK -> runCatching { URI(value).let { it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() } }.getOrDefault(false)
        BridgeAction.WEB_UI -> value.isNotBlank() && '/' !in value && '\\' !in value && value != "." && value != ".."
    }

    /** Bind the existing app proof to the exact action shown in the phone's confirmation. */
    fun authenticationAction(action: BridgeAction, value: String, title: String): String = "$OPERATION:${action.name}:${value.length}:$value:$title"
}
