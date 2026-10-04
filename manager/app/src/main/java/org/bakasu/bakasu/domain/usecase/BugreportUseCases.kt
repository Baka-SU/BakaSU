package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.logging.BugreportRepository
import java.io.File

class GenerateBugreportUseCase(
    private val repository: BugreportRepository,
) {
    operator fun invoke(): File = repository.create()
}
