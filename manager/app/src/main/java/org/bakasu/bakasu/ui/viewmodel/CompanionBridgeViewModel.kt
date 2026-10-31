package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.bakasu.bakasu.domain.model.BridgeException
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.bakasu.bakasu.domain.model.BridgePeer
import org.bakasu.bakasu.domain.usecase.ConfigureCompanionBridgeUseCase
import org.bakasu.bakasu.domain.usecase.GetBridgePeersUseCase
import org.bakasu.bakasu.domain.usecase.ObserveCompanionBridgeUseCase

class CompanionBridgeViewModel(
    observe: ObserveCompanionBridgeUseCase,
    private val getPeers: GetBridgePeersUseCase,
    private val configure: ConfigureCompanionBridgeUseCase,
) : ViewModel() {
    val state = observe()
    private val mutablePeers = MutableStateFlow<List<BridgePeer>>(emptyList())
    val peers = mutablePeers.asStateFlow()
    private val mutableError = MutableStateFlow<BridgeFailure?>(null)
    val error = mutableError.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()

    fun clearError() {
        mutableError.value = null
    }
    fun failed(reason: BridgeFailure) {
        mutableError.value = reason
    }
    fun refresh() = run { configure.refresh() }

    /** Loads the saved link, restores a system association and the paired-device list in one pass. */
    fun initialize() = run {
        configure.refresh()
        configure.restore()
        mutablePeers.value = getPeers()
    }

    /** Bluetooth-paired devices; the system pairing is the only requirement for a watch link. */
    fun loadPeers() = run { mutablePeers.value = getPeers() }
    fun link(address: String, name: String) = run { configure.link(address, name) }
    fun observePresence(address: String) = run { configure.observePresence(address) }
    fun setAccess(enabled: Boolean) = run {
        val peer = state.value.peer ?: throw BridgeException(BridgeFailure.SETUP_REQUIRED)
        if (enabled) configure.link(peer.address, peer.name) else configure.enable(false)
    }
    fun unassociate(address: String) = run { configure.unassociate(address) }
    fun disconnect() = run { configure.disconnect() }

    private fun run(action: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        clearError()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableError.value = (error as? BridgeException)?.reason ?: BridgeFailure.CONNECTION
            } finally {
                mutableBusy.value = false
            }
        }
    }
}
