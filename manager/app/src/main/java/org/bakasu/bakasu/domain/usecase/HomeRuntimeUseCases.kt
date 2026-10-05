package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.network.NetworkStatusRepository
import org.bakasu.bakasu.data.system.HomeRuntimeRepository

class GetHomeBasicInfoUseCase(private val repository: HomeRuntimeRepository) {
    suspend operator fun invoke(
        managerUapiVersion: Int,
        includeSelinuxStatus: Boolean = true,
    ) = repository.getBasicInfo(managerUapiVersion, includeSelinuxStatus)
}

class IsNetworkAvailableUseCase(private val repository: NetworkStatusRepository) {
    operator fun invoke() = repository.isAvailable()
}
