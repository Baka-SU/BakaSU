package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.haptics.CrownHapticsRepository

class IsXiaomiWatch5UseCase(private val repository: CrownHapticsRepository) {
    operator fun invoke() = repository.isXiaomiWatch5
}

class GetXiaomiWatch5ModeUseCase(private val repository: CrownHapticsRepository) {
    operator fun invoke() = repository.isXiaomiWatch5ModeEnabled()
}

class SetXiaomiWatch5ModeUseCase(private val repository: CrownHapticsRepository) {
    operator fun invoke(enabled: Boolean) = repository.setXiaomiWatch5ModeEnabled(enabled)
}
