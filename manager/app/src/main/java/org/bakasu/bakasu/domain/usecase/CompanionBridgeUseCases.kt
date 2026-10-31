package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.webui.CompanionBridgeRepository
import org.bakasu.bakasu.domain.model.BridgePeer

class ObserveCompanionBridgeUseCase(private val repository: CompanionBridgeRepository) {
    operator fun invoke() = repository.state
}
class GetBridgePeersUseCase(private val repository: CompanionBridgeRepository) {
    /** Bluetooth-paired devices, the only ones a watch link can be set up with. */
    operator fun invoke(): List<BridgePeer> = repository.bondedPeers()
}
class ConfigureCompanionBridgeUseCase(private val repository: CompanionBridgeRepository) {
    /** Restores the watch chosen in system settings before the user grants access. */
    fun restore() = repository.associatedPeer()?.let { repository.adopt(it.address, it.name) }
    fun link(address: String, name: String) = repository.link(address, name)
    fun observePresence(address: String) = repository.observePresence(address)
    fun enable(enabled: Boolean) = repository.setEnabled(enabled)
    fun unassociate(address: String) = repository.unassociate(address)
    fun disconnect() = repository.disconnect()
    fun refresh() = repository.refresh()
}
