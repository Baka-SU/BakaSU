package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.flash.FlashRepository
import org.bakasu.bakasu.domain.model.FlashOperation

class ExecuteFlashOperationUseCase(private val repository: FlashRepository) {
    operator fun invoke(operation: FlashOperation) = repository.execute(operation)
}
