package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.logging.SulogRepository

class ObserveSulogStateUseCase(private val repository: SulogRepository) {
    operator fun invoke() = repository.state
}

class RefreshSulogUseCase(private val repository: SulogRepository) {
    suspend operator fun invoke(preferredFilePath: String?) = repository.refresh(preferredFilePath)

    suspend fun silently() = repository.refresh(silent = true)

    suspend fun selectFile(path: String) = repository.selectFile(path)
}

class EnableSulogUseCase(private val repository: SulogRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setEnabled(enabled)
}

class CleanSulogUseCase(private val repository: SulogRepository) {
    suspend operator fun invoke(path: String) = repository.clean(path)
}
