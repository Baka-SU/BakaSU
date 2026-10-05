package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.startup.StartupRepository

class ObserveStartupStateUseCase(
    private val repository: StartupRepository,
) {
    operator fun invoke() = repository.state
}
