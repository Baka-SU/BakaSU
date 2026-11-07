package org.bakasu.bakasu.data.module

import kotlinx.coroutines.runBlocking
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.domain.model.ModuleUpdateMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleUpdateRefreshTest {
    private val update = ModuleUpdateMetadata("https://example.com/module.zip", "2", "https://example.com/log", 2)
    private val installed = InstalledModule(
        id = "module", name = "Module", author = "Author", version = "1", versionCode = 1,
        description = "", enabled = true, update = false, remove = false,
        updateJson = "https://example.com/update.json", hasWebUi = false, hasActionScript = false,
        metamodule = false, actionIconPath = null, webUiIconPath = null, dirId = "module", moduleUpdate = update,
    )

    @Test
    fun togglingOneModulePreservesUpdatesForItAndOtherModulesBeforeAndAfterRefresh() = runBlocking {
        var previous = listOf(installed, installed.copy(id = "other", dirId = "other"))
        var requests = 0
        for (enabled in listOf(false, true, false, true)) {
            val parsed = previous.map { it.copy(enabled = if (it.id == "module") enabled else it.enabled, moduleUpdate = null) }
            val cached = parsed.map { module -> module.withCachedUpdate(previous.find { it.id == module.id }) }
            assertTrue(cached.all { it.moduleUpdate == update })
            previous = cached.map { module ->
                module.refreshUpdate(manual = false) { _, _ ->
                    requests++
                    Result.success(update)
                }
            }
            assertTrue(previous.all { it.moduleUpdate == update })
            assertEquals(enabled, previous.first().enabled)
        }
        assertEquals(0, requests)
    }

    @Test
    fun changedIdentityVersionOrSourceDoesNotReuseAnUpdate() {
        for (module in listOf(
            installed.copy(id = "different"),
            installed.copy(versionCode = 2),
            installed.copy(updateJson = "https://other.example/update.json"),
            installed.copy(updateJson = ""),
        )) {
            assertNull(module.withCachedUpdate(installed).moduleUpdate)
        }
        assertNull(installed.withCachedUpdate(null).moduleUpdate)
    }

    @Test
    fun firstCheckWorksForEnabledAndDisabledModules() = runBlocking {
        for (enabled in listOf(true, false)) {
            var requests = 0
            val result = installed.copy(enabled = enabled, moduleUpdate = null).refreshUpdate(manual = false) { url, version ->
                assertEquals(installed.updateJson, url)
                assertEquals(1, version)
                requests++
                Result.success(update)
            }
            assertEquals(update, result.moduleUpdate)
            assertEquals(1, requests)
        }
    }

    @Test
    fun manualRefreshReplacesTheKnownUpdateAndCanClearIt() = runBlocking {
        val newer = update.copy(version = "3", versionCode = 3)
        val refreshed = installed.refreshUpdate(manual = true) { _, _ -> Result.success(newer) }
        assertEquals(newer, refreshed.moduleUpdate)
        val current = refreshed.refreshUpdate(manual = true) { _, _ -> Result.success(null) }
        assertNull(current.moduleUpdate)
    }

    @Test
    fun failedManualRefreshKeepsKnownUpdate() = runBlocking {
        val refreshed = installed.refreshUpdate(manual = true) { _, _ -> Result.failure(IllegalStateException("offline")) }
        assertEquals(update, refreshed.moduleUpdate)
    }

    @Test
    fun missingUpdateCanBeRetriedOnAutomaticRefresh() = runBlocking {
        val failed = installed.copy(moduleUpdate = null).refreshUpdate(manual = false) { _, _ -> Result.failure(IllegalStateException("offline")) }
        val retried = failed.refreshUpdate(manual = false) { _, _ -> Result.success(update) }
        assertEquals(update, retried.moduleUpdate)
    }

    @Test
    fun blankUpdateSourceDoesNotMakeARequest() = runBlocking {
        var requested = false
        val result = installed.copy(updateJson = "", moduleUpdate = null).refreshUpdate(manual = true) { _, _ ->
            requested = true
            Result.success(update)
        }
        assertFalse(requested)
        assertNull(result.moduleUpdate)
    }
}
