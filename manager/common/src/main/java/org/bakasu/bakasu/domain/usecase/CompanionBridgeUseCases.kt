package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.webui.CompanionBridgeRepository
import org.bakasu.bakasu.domain.model.BridgeRequest

class ObserveCompanionBridgeUseCase(private val repository: CompanionBridgeRepository) {
    operator fun invoke() = repository.state
    val incomingRequest get() = repository.incomingRequest
    val listening get() = repository.listening
    val transfer get() = repository.transfer
    val permissionRequest get() = repository.permissionRequest
}
class ConfigureCompanionBridgeUseCase(private val repository: CompanionBridgeRepository) {
    val needsReceiverSetup get() = repository.needsReceiverSetup
    fun observePresence(address: String) = repository.observePresence(address)
    fun enable(enabled: Boolean) = repository.setEnabled(enabled)
    fun disconnect() = repository.disconnect()
    fun refresh() = repository.refresh()
    fun remember(enabled: Boolean) = repository.setRemembered(enabled)
    fun resumeReceiver() {
        if (repository.state.value.enabled) repository.startServer()
    }
    fun approve(id: String, remember: Boolean?) = repository.approveRequest(id, remember)
    fun presented(id: String) = repository.presentRequest(id)
    fun launch(id: String, open: (BridgeRequest) -> Boolean) = repository.launchRequest(id, open)
    fun answerPermission(id: String, granted: Boolean) = repository.answerPermission(id, granted)
    fun cancelTransfer() = repository.cancelTransfer()
    fun dismissTransfer() = repository.dismissTransfer()
}
