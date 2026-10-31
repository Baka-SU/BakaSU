package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.webui.CompanionBridgeRepository

class ObserveCompanionBridgeUseCase(private val repository: CompanionBridgeRepository) {
    operator fun invoke() = repository.state
}
class ConfigureCompanionBridgeUseCase(private val repository: CompanionBridgeRepository) {
    /** Restores the watch chosen in system settings before the user grants access. */
    fun restore() = repository.associatedPeer()?.let { repository.adopt(it.address, it.name) }
    fun link(address: String, name: String) = repository.link(address, name)
    fun enable(enabled: Boolean) = repository.setEnabled(enabled)
    fun unassociate(address: String) = repository.unassociate(address)
    fun disconnect() = repository.disconnect()
    fun refresh() = repository.refresh()
}
