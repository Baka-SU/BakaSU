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
import org.bakasu.bakasu.domain.usecase.PairCompanionBridgeUseCase

class CompanionBridgeViewModel(
    observe: ObserveCompanionBridgeUseCase,
    private val getPeers: GetBridgePeersUseCase,
    private val pairBridge: PairCompanionBridgeUseCase,
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
    fun refresh() = run {
        configure.refresh()
        mutablePeers.value = getPeers()
    }
    fun associate(address: String, name: String) = run { configure.associate(address, name) }
    fun generateCode() {
        state.value.peer?.let { associate(it.address, it.name) }
    }
    fun enable(enabled: Boolean) = run { configure.enable(enabled) }
    fun disconnect() = run { configure.disconnect() }
    fun pair(peer: BridgePeer, code: String) = run { pairBridge(peer, code) }

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
