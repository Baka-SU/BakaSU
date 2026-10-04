package com.bakasu.bakasu.domain.usecase

import com.bakasu.bakasu.data.update.ManagerUpdateRepository
import com.bakasu.bakasu.domain.model.ManagerUpdateChannel
import com.bakasu.bakasu.domain.model.ManagerUpdateInfo

class CheckManagerUpdateUseCase(
    private val repository: ManagerUpdateRepository,
) {
    suspend operator fun invoke(channel: ManagerUpdateChannel): ManagerUpdateInfo? =
        when (channel) {
            ManagerUpdateChannel.STABLE -> repository.checkStableUpdate()
            ManagerUpdateChannel.BETA -> repository.checkBetaUpdate()
        }
}
