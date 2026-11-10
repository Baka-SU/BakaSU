package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.module.ModuleIconRepository
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.domain.model.ModuleUpdateMetadata
import org.bakasu.bakasu.domain.usecase.LoadModuleIconUseCase
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadPhase
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module

@RunWith(Parameterized::class)
class WearModuleUpdateTest(private val round: Boolean) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val update = ModuleUpdateMetadata("https://example.com/module.zip", "2", "https://example.com/log", 2)
    private val module = InstalledModule(
        id = "module", name = "Module", author = "Author", version = "1", versionCode = 1,
        description = "", enabled = true, update = false, remove = false,
        updateJson = "https://example.com/update.json", hasWebUi = false, hasActionScript = false,
        metamodule = false, actionIconPath = null, webUiIconPath = null, dirId = "module", moduleUpdate = update,
    )
    private var openedChangelog = false
    private var downloaded = false

    @Test
    fun downloadIsAvailableBeforeOpeningChangelog() {
        showPage(module)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithText(compose.activity.getString(R.string.wear_update_download)).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertTrue(downloaded)
            assertFalse(openedChangelog)
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.onNodeWithText(compose.activity.getString(R.string.module_changelog)).performClick()
        compose.runOnIdle { assertTrue(openedChangelog) }
    }

    @Test
    fun missingChangelogDoesNotBlockDownload() {
        showPage(module.copy(moduleUpdate = update.copy(changelog = "")))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.onNodeWithText(compose.activity.getString(R.string.module_changelog)).assertIsNotEnabled()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithText(compose.activity.getString(R.string.wear_update_download)).assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(downloaded) }
    }

    @Test
    fun longModuleNameAndVersionRemainUnclipped() {
        val installed = module.copy(name = "A module with a deliberately long name", moduleUpdate = update.copy(version = "2026.11.08-long-release-version"))
        showPage(installed)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        val version = compose.activity.getString(R.string.wear_update_version, installed.version, installed.moduleUpdate!!.version)
        for (text in listOf(installed.name, version)) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse(layouts.single().hasVisualOverflow)
        }
    }

    @Test
    fun installedModuleUpdateCarriesItsArtworkIntoTheDownloadRoute() {
        val iconUrl = "https://icons.example/update.png"
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val icons = ModuleIconRepository(
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test").body(bytes.toResponseBody()).build()
            }.build(),
        )
        runBlocking { icons.load(iconUrl, null) }
        val app = koinApplication { modules(module { single { LoadModuleIconUseCase(icons) } }) }
        val source = CatalogModule("module", "Module", "", emptyList(), "", false, 0, "", "", "", "", 0, null, "", emptyList(), "https://repository.example/", iconUrl = iconUrl)
        val navigator = WearNavigator(NavBackStack<NavKey>(WearRoute.ModuleUpdate("module")), {}, {}, {})
        val installer = WearModuleInstaller(navigator, mutableStateOf(null), {}, {})
        val lookup = CompletableDeferred<CatalogModule?>()
        try {
            compose.setContent {
                KoinIsolatedContext(app) {
                    MaterialTheme {
                        AppScaffold(timeText = {}) {
                            val route = navigator.top
                            if (route is WearRoute.OnlineModuleDownload) {
                                WearModuleDownloadScreen(route.name, ModuleDownloadUiState(phase = ModuleDownloadPhase.DOWNLOADING, downloadedBytes = 89, totalBytes = 100), { 0.89f }, route.iconUrl, route.iconOrigin)
                            } else {
                                WearModuleUpdatePage(module, {}, installer::update, {}, findSource = { lookup.await() })
                            }
                        }
                    }
                }
            }
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
            compose.onNodeWithText(compose.activity.getString(R.string.wear_update_download)).performClick()
            compose.runOnIdle {
                assertTrue(navigator.top is WearRoute.ModuleUpdate)
                lookup.complete(source)
            }
            compose.waitForIdle()
            val route = navigator.top as WearRoute.OnlineModuleDownload
            assertEquals(iconUrl, route.iconUrl)
            assertTrue(route.update)
            val image = compose.onNodeWithTag("module-download-icon").captureToImage().asAndroidBitmap()
            assertEquals(android.graphics.Color.GREEN, image.getPixel(image.width / 2, image.height / 2))
            File(compose.activity.getExternalFilesDir(null), "update-download-icon-$round.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            app.close()
        }
    }

    @Test
    fun pendingIconLookupCannotOpenDownloadOverAnotherPage() {
        val lookup = CompletableDeferred<CatalogModule?>()
        val active = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    CompositionLocalProvider(LocalScreenIsActive provides active.value) {
                        WearModuleUpdatePage(module, {}, { _, _, _ -> downloaded = true }, {}, findSource = { lookup.await() })
                    }
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithText(compose.activity.getString(R.string.wear_update_download)).performClick()
        compose.runOnIdle { active.value = false }
        compose.runOnIdle { lookup.complete(null) }
        compose.runOnIdle { assertFalse(downloaded) }
    }

    private fun showPage(installed: InstalledModule) {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        Box(Modifier.size(192.dp)) {
                            WearModuleUpdatePage(
                                installed,
                                onBack = {},
                                onDownload = { selected, metadata, _ ->
                                    assertEquals(installed, selected)
                                    assertEquals(installed.moduleUpdate, metadata)
                                    downloaded = true
                                },
                                onChangelog = { openedChangelog = true },
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}")
        fun screens() = listOf(false, true)
    }
}
