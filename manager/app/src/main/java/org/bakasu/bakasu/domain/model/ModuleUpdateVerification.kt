package org.bakasu.bakasu.domain.model

sealed interface ModuleUpdateVerification {
    data object Legacy : ModuleUpdateVerification
    data class Registry(
        val zipUrl: String,
        val expected: ExpectedFile,
        val release: ModuleRelease,
        val moduleId: String,
        val buildId: String,
        val repositoryUrl: String = RepositorySource.KERNEL_SU_URL,
    ) : ModuleUpdateVerification
    data object Rejected : ModuleUpdateVerification
}
