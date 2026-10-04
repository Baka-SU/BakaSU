package com.bakasu.bakasu.domain.usecase

import com.bakasu.bakasu.data.AppSettingsRepository
import com.bakasu.bakasu.data.startup.ApplicationInitializationRepository
import com.bakasu.bakasu.data.startup.StartupRepository

class InitializeApplicationUseCase(
    private val settingsRepository: AppSettingsRepository,
    private val startupRepository: StartupRepository,
    private val initializationRepository: ApplicationInitializationRepository,
) {
    suspend operator fun invoke() {
        runCatching {
            settingsRepository.preload()
            initializationRepository.initialize()
        }.onSuccess {
            startupRepository.markReady()
        }.onFailure { error ->
            startupRepository.markFailed(error)
        }
    }
}
