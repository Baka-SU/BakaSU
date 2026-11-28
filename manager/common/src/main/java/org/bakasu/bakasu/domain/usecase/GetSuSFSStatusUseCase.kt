package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.susfs.SuSFSRepository

class GetSuSFSStatusUseCase(private val repository: SuSFSRepository) {
    suspend operator fun invoke() = repository.getStatus()
}
