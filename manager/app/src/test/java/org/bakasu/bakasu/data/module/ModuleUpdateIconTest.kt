package org.bakasu.bakasu.data.module

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.bakasu.bakasu.data.module.registry.KernelSuRegistryRepository
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.ModuleCatalogResult
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.usecase.FindModuleUpdateSourceUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModuleUpdateIconTest {
    private val zip = "https://github.com/example/module/releases/download/v2/module.zip"
    private val source = "https://modules.example.com/"
    private val registry = mockk<KernelSuRegistryRepository>()
    private val modules = MutableStateFlow<List<CatalogModule>>(emptyList())
    private val repository = spyk(ModuleCatalogRepository(mockk(), mockk<RepositorySourceStore> { every { load() } returns emptyList() }, registry, mockk()))

    init {
        every { registry.sourceForUpdate(any()) } returns null
        every { repository.modules } returns modules
    }

    @Test
    fun knownSourceLoadsDetailBeforeTheRepositoryHasBeenBrowsed() = runTest {
        val module = module(source)
        every { registry.sourceForUpdate("https://modules.example.com/update/example.json") } returns source
        coEvery { repository.get("example", source, any()) } returns ModuleCatalogResult.Success(module)
        assertEquals(module, FindModuleUpdateSourceUseCase(repository)("example", "https://modules.example.com/update/example.json", zip))
        coVerify(exactly = 1) { repository.get("example", source, any()) }
    }

    @Test
    fun duplicateIdsNeedAnUnambiguousMatchingRelease() = runTest {
        val first = module(source)
        val second = module("https://other.example.com/")
        modules.value = listOf(first, second.copy(releases = emptyList()))
        assertEquals(first, selectModuleUpdateSource(modules.value, "example", zip))
        modules.value = listOf(first, second)
        assertNull(selectModuleUpdateSource(modules.value, "example", zip))
        assertNull(selectModuleUpdateSource(modules.value, "another-id", zip))
        assertNull(selectModuleUpdateSource(modules.value, "example", "https://example.com/other.zip"))
    }

    @Test
    fun missingArtworkOrFailedLookupDoesNotPreventTheUpdate() = runTest {
        modules.value = listOf(module(source).copy(iconUrl = null))
        assertNull(selectModuleUpdateSource(modules.value, "example", zip)?.iconUrl)
        coEvery { repository.findUpdateSource("example", "", zip) } throws java.io.IOException("offline")
        assertNull(FindModuleUpdateSourceUseCase(repository)("example", "", zip))
    }

    private fun module(sourceUrl: String) = CatalogModule(
        "example", "Example", "Author", emptyList(), "Description", false, 0, "", "", "v2", "", 2,
        null, "", listOf(ModuleRelease("v2", "v2", "", listOf(ModuleReleaseAsset("module.zip", zip, null, null)))),
        sourceUrl, iconUrl = "https://example.com/icon.png",
    )
}
