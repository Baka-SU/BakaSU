package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.webui.CompanionBridgeRepository
import org.bakasu.bakasu.domain.model.BridgePeer

class ObserveCompanionBridgeUseCase(private val repository: CompanionBridgeRepository) {
    operator fun invoke() = repository.state
}
class GetBridgePeersUseCase(private val repository: CompanionBridgeRepository) {
    operator fun invoke(): List<BridgePeer> = repository.bondedPeers()
}
class PairCompanionBridgeUseCase(private val repository: CompanionBridgeRepository) {
    suspend operator fun invoke(peer: BridgePeer, code: String) = repository.pair(peer, code)
}
class ConfigureCompanionBridgeUseCase(private val repository: CompanionBridgeRepository) {
    fun associate(address: String, name: String) = repository.beginPairing(address, name)
    fun enable(enabled: Boolean) = repository.setEnabled(enabled)
    fun disconnect() = repository.disconnect()
    fun refresh() = repository.refresh()
}
