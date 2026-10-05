package org.bakasu.bakasu.domain.usecase

import java.io.File
import org.bakasu.bakasu.data.logging.BugreportRepository

class GenerateBugreportUseCase(
    private val repository: BugreportRepository,
) {
    operator fun invoke(): File = repository.create()
}
