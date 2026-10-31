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
import org.bakasu.bakasu.domain.usecase.ConfigureCompanionBridgeUseCase
import org.bakasu.bakasu.domain.usecase.ObserveCompanionBridgeUseCase

class CompanionBridgeViewModel(
    observe: ObserveCompanionBridgeUseCase,
    private val configure: ConfigureCompanionBridgeUseCase,
) : ViewModel() {
    val state = observe()
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

    /** Picks up a watch associated in system settings before this page was opened. */
    fun restoreAssociation() = run { configure.restore() }
    fun link(address: String, name: String) = run { configure.link(address, name) }
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
