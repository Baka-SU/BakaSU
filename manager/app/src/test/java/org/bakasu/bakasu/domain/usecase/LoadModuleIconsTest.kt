package org.bakasu.bakasu.domain.usecase

import android.graphics.Bitmap
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.bakasu.bakasu.data.module.ModuleIconRepository
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.RegistryModuleMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoadModuleIconsTest {
    private val repository = mockk<ModuleIconRepository>()
    private val loader = LoadModuleIconUseCase(repository)

    @Test
    fun waitsForTheLastIconAndKeepsSuccessfulAndFallbackResults() = runTest {
        val slow = CompletableDeferred<Bitmap?>()
        val bitmap = mockk<Bitmap>()
        coEvery { repository.load("https://example.com/fast", null) } returns bitmap
        coEvery { repository.load("https://example.com/slow", null) } coAnswers { slow.await() }
        coEvery { repository.load("https://example.com/broken", null) } throws IOException()
        val modules = listOf(module("fast"), module("slow"), module("broken"), module("missing").copy(iconUrl = null))
        val batch = async { loader.loadAll(modules) }
        runCurrent()
        assertFalse(batch.isCompleted)
        slow.complete(null)
        val results = batch.await()
        assertEquals(modules.map { it.catalogId }.toSet(), results.keys)
        assertSame(bitmap, results[modules[0].catalogId])
        modules.drop(1).forEach { assertNull(results[it.catalogId]) }
    }

    @Test
    fun deduplicatesSharedIconsAndPreservesRegistryOrigin() = runTest {
        val bitmap = mockk<Bitmap>()
        val first = module("first").copy(iconUrl = "", registryMetadata = RegistryModuleMetadata("build", emptyList(), "https://example.com/shared", null, null))
        val second = first.copy(moduleId = "second")
        coEvery { repository.load("https://example.com/shared", first.repositoryUrl) } returns bitmap
        val results = loader.loadAll(listOf(first, second))
        assertSame(bitmap, results[first.catalogId])
        assertSame(bitmap, results[second.catalogId])
        coVerify(exactly = 1) { repository.load("https://example.com/shared", first.repositoryUrl) }
    }

    @Test
    fun cancellationDoesNotScheduleTheRemainingIcons() = runTest {
        val pending = CompletableDeferred<Bitmap?>()
        coEvery { repository.load(any(), any()) } coAnswers { pending.await() }
        val batch = async { loader.loadAll((1..20).map { module(it.toString()) }) }
        runCurrent()
        coVerify(exactly = 4) { repository.load(any(), any()) }
        batch.cancel()
        runCurrent()
        pending.complete(null)
        runCurrent()
        coVerify(exactly = 4) { repository.load(any(), any()) }
    }

    private fun module(id: String) = CatalogModule(
        moduleId = id, moduleName = id, authors = "", authorList = emptyList(), summary = "", metamodule = false,
        stargazerCount = 0, updatedAt = "", createdAt = "", latestRelease = "", latestReleaseTime = "", latestVersionCode = 0,
        latestAsset = null, sourceUrl = "", releases = emptyList(), repositoryUrl = "https://example.com/", iconUrl = "https://example.com/$id",
    )
}
