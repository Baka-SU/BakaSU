package com.bakasu.bakasu.domain.usecase

import com.bakasu.bakasu.data.susfs.SuSFSRepository

class GetSuSFSStatusUseCase(private val repository: SuSFSRepository) {
    suspend operator fun invoke() = repository.getStatus()
}

