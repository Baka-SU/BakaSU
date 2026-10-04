package com.bakasu.bakasu.domain.usecase

import com.bakasu.bakasu.data.startup.StartupRepository

class ObserveStartupStateUseCase(
    private val repository: StartupRepository,
) {
    operator fun invoke() = repository.state
}
