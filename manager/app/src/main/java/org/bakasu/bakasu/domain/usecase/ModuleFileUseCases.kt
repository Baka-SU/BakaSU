package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.download.DownloadRepository
import org.bakasu.bakasu.data.file.ModuleFileRepository
import org.bakasu.bakasu.domain.model.ExpectedFile

class IsModuleUriAccessibleUseCase(private val repository: ModuleFileRepository) {
    operator fun invoke(uri: String) = repository.isUriAccessible(uri)
}

class TakeModuleUriPermissionUseCase(private val repository: ModuleFileRepository) {
    operator fun invoke(uri: String) = repository.takePersistableUriPermission(uri)
}

class ExtractModuleNameUseCase(private val repository: ModuleFileRepository) {
    operator fun invoke(uri: String) = repository.extractModuleName(uri)
}

class ExtractModuleIdUseCase(private val repository: ModuleFileRepository) {
    operator fun invoke(uri: String) = repository.extractModuleId(uri)
}

/**
 * Checks a downloaded repository file against the size and SHA-256 the repository published, so a
 * damaged or replaced ZIP is not installed. Files without a published digest are not checked.
 */
class VerifyModuleDownloadUseCase(private val repository: DownloadRepository) {
    suspend operator fun invoke(uri: String, size: Long, sha256: String): Boolean = sha256.isBlank() || repository.verify(uri, ExpectedFile(size, sha256))
}
