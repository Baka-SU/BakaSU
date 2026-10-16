package org.bakasu.bakasu.domain.model

sealed interface ModuleUpdateVerification {
    data object Legacy : ModuleUpdateVerification
    data class Registry(
        val zipUrl: String,
        val expected: ExpectedFile,
        val release: ModuleRelease,
        val moduleId: String,
        val buildId: String,
    ) : ModuleUpdateVerification
    data object Rejected : ModuleUpdateVerification
}
