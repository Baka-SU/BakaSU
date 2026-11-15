package org.bakasu.bakasu.ui.wear

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.bakasu.bakasu.domain.model.ExpectedFile
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleUpdateMetadata
import org.bakasu.bakasu.domain.model.ModuleUpdateVerification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearModuleInstallTest {
    private val update = ModuleUpdateMetadata("https://example.com/update.zip", "2", "", 2)
    private val installed = InstalledModule(
        id = "module", name = "Module", author = "", version = "1", versionCode = 1,
        description = "", enabled = true, update = false, remove = false,
        updateJson = "https://modules.kernelsu.org/module/update.json", hasWebUi = false, hasActionScript = false,
        metamodule = false, actionIconPath = null, webUiIconPath = null, dirId = "module", moduleUpdate = update,
    )
    private val navigator = WearNavigator(NavBackStack<NavKey>(WearRoute.Main), {}, {}, {})
    private val installer = WearModuleInstaller(navigator, mutableStateOf(null), {}, {})

    @Test
    fun registryUpdateKeepsTheVerifiedUrlAndDigestThroughNavigation() {
        val expected = ExpectedFile(123, "a".repeat(64))
        val verifiedUrl = "https://github.com/owner/module/releases/download/v2/module.zip"
        val verification = ModuleUpdateVerification.Registry(verifiedUrl, expected, ModuleRelease("2", "v2", "", emptyList()), "module", "build")
        installer.update(installed, update, verification)
        val route = navigator.top as WearRoute.OnlineModuleDownload
        assertTrue(route.update)
        assertTrue(route.asset().verificationRequired)
        assertEquals(verifiedUrl, route.asset().downloadUrl)
        assertEquals(expected, route.asset().expected)
    }

    @Test
    fun rejectedUpdateDoesNotOpenADownload() {
        installer.update(installed, update, ModuleUpdateVerification.Rejected)
        assertEquals(WearRoute.Main, navigator.top)
    }

    @Test
    fun legacyUpdateStillUsesItsOriginalDownload() {
        installer.update(installed, update, ModuleUpdateVerification.Legacy)
        val route = navigator.top as WearRoute.OnlineModuleDownload
        assertEquals(update.zipUrl, route.asset().downloadUrl)
        assertFalse(route.asset().verificationRequired)
        assertNull(route.asset().expected)
    }

}
