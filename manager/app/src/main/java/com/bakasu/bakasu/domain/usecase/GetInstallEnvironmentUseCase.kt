package com.bakasu.bakasu.domain.usecase

import com.bakasu.bakasu.data.flash.FlashRepository
import com.bakasu.bakasu.domain.model.InstallEnvironment

class GetInstallEnvironmentUseCase(
    private val repository: FlashRepository,
) {
    fun cached(): InstallEnvironment? = repository.installEnvironment.value

    suspend operator fun invoke(forceRefresh: Boolean = false) =
        repository.getInstallEnvironment(forceRefresh)
}
