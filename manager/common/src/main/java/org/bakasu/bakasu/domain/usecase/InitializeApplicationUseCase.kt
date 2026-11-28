package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.AppSettingsRepository
import org.bakasu.bakasu.data.startup.ApplicationInitializationRepository
import org.bakasu.bakasu.data.startup.StartupRepository
import org.bakasu.bakasu.data.webui.CompanionBridgeRepository

class InitializeApplicationUseCase(
    private val settingsRepository: AppSettingsRepository,
    private val startupRepository: StartupRepository,
    private val initializationRepository: ApplicationInitializationRepository,
    private val companionBridgeRepository: CompanionBridgeRepository,
) {
    suspend operator fun invoke() {
        runCatching {
            settingsRepository.preload()
            // Register the bridge before the first Activity resumes, then load its saved peer.
            companionBridgeRepository.refresh()
            initializationRepository.initialize()
        }.onSuccess {
            startupRepository.markReady()
        }.onFailure { error ->
            startupRepository.markFailed(error)
        }
    }
}
