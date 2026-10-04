package com.bakasu.bakasu.domain.usecase

import com.bakasu.bakasu.data.flash.FlashRepository
import com.bakasu.bakasu.domain.model.FlashOperation

class ExecuteFlashOperationUseCase(private val repository: FlashRepository) {
    operator fun invoke(operation: FlashOperation) = repository.execute(operation)
}
