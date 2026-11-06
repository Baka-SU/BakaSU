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
    val request = observe.incomingRequest
    val needsReceiverSetup get() = configure.needsReceiverSetup
    fun resumeReceiver() = run { configure.resumeReceiver() }
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

    /** Refreshes the receiver and remembered watch settings. */
    fun initialize() = run {
        configure.refresh()
    }

    fun observePresence(address: String) = run { configure.observePresence(address) }
    fun setAccess(enabled: Boolean) = run { configure.enable(enabled) }
    fun setRemembered(enabled: Boolean) = run { configure.remember(enabled) }
    fun approve(id: String, remember: Boolean?) = configure.approve(id, remember)
    fun presented(id: String) = configure.presented(id)
    fun completed(id: String, opened: Boolean) = configure.completed(id, opened)
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
