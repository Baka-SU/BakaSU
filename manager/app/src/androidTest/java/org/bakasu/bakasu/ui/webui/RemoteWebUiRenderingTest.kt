package org.bakasu.bakasu.ui.webui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.AppSettingsRepository
import org.bakasu.bakasu.data.webui.WebUiBackend
import org.bakasu.bakasu.domain.model.WebUiCommandResult
import org.bakasu.bakasu.domain.model.WebUiProcess
import org.bakasu.bakasu.ui.wear.WearManagerTheme
import org.bakasu.bakasu.ui.wear.WearWebUIScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

class RemoteWebUiRenderingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun phoneLoadsWatchModuleWithoutLocalModuleFiles() {
        val backend = TestBackend()
        val state = moduleState()
        var loaded = 0
        try {
            createPage(state, backend) { loaded++ }
            assertEquals(0, loaded)
            compose.setContent { MaterialTheme { WebUIScreen(state) } }
            compose.waitUntil(15_000) { loaded > 0 }
            assertTrue(backend.paths.contains("/data/adb/modules/test_module/webroot/index.html"))
            var body: String? = null
            compose.runOnIdle { state.webView!!.evaluateJavascript("document.body.innerText") { body = it } }
            compose.waitUntil { body != null }
            assertEquals("\"Watch module\"", body)
            screenshot("phone-module-loaded", state)
        } finally {
            compose.runOnIdle { state.dispose() }
        }
    }

    @Test
    fun roundWatchModuleStartsLoadingFromItsFirstLayout() = watchModuleStartsLoading(round = true)

    @Test
    fun squareWatchModuleStartsLoadingFromItsFirstLayout() = watchModuleStartsLoading(round = false)

    private fun watchModuleStartsLoading(round: Boolean) {
        val html = InstrumentationRegistry.getInstrumentation().context.assets.open("wear-webview-viewport.html").bufferedReader().use { it.readText() }
        val backend = TestBackend(html = html)
        val state = moduleState()
        try {
            createPage(state, backend) {}
            compose.setContent {
                val configuration = Configuration(LocalConfiguration.current).apply {
                    screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                        if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
                }
                CompositionLocalProvider(LocalConfiguration provides configuration) {
                    WearManagerTheme { WearWebUIScreen(state) {} }
                }
            }
            compose.waitUntil(10_000) { state.pageLoaded }
            assertTrue(state.isUrlLoaded)
            assertTrue(backend.paths.contains("/data/adb/modules/test_module/webroot/index.html"))
            compose.runOnIdle { assertTrue(state.webView!!.height > 0) }
            var visibleContent: String? = null
            compose.runOnIdle {
                state.webView!!.evaluateJavascript("document.querySelector('main').getBoundingClientRect().height > 40 && document.body.innerText.includes('Module status')") { visibleContent = it }
            }
            compose.waitUntil { visibleContent != null }
            assertEquals("true", visibleContent)
            compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
        } finally {
            screenshot("wear-module-${if (round) "round" else "square"}", state)
            compose.runOnIdle { state.dispose() }
        }
    }

    @Test
    fun missingWatchHomepageReportsPageFailureWithoutCallingOnLoaded() {
        val state = moduleState()
        var loaded = 0
        try {
            createPage(state, TestBackend(missingHomepage = true)) { loaded++ }
            compose.setContent { MaterialTheme { WebUIScreen(state) } }
            compose.waitUntil(15_000) { state.uiEvent is WebUIEvent.Error }
            assertEquals(compose.activity.getString(R.string.wear_webui_load_failed), (state.uiEvent as WebUIEvent.Error).message)
            assertEquals(0, loaded)
        } finally {
            compose.runOnIdle { state.dispose() }
        }
    }

    private fun moduleState() = WebUIState().apply {
        moduleName = "Watch module"
        modDir = "/data/adb/modules/test_module"
    }

    private fun screenshot(name: String, state: WebUIState) {
        compose.waitForIdle()
        if (state.pageLoaded) {
            val drawn = CountDownLatch(1)
            compose.runOnIdle {
                val webView = state.webView!!
                webView.postVisualStateCallback(
                    0,
                    object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            webView.postOnAnimation { webView.postOnAnimation { drawn.countDown() } }
                        }
                    },
                )
            }
            assertTrue(drawn.await(5, TimeUnit.SECONDS))
        }
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(500, 5_000)
        val bitmap = automation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun createPage(state: WebUIState, backend: WebUiBackend, onLoaded: () -> Unit) {
        compose.runOnIdle {
            createWebView(
                compose.activity,
                state,
                backend,
                GlobalContext.get().get<AppSettingsRepository>(),
                restrictToModule = true,
                colorsCssProvider = { "" },
                onPageLoaded = onLoaded,
            )
        }
    }

    private class TestBackend(private val missingHomepage: Boolean = false, private val html: String = "<html><body>Watch module</body></html>") : WebUiBackend {
        val paths = CopyOnWriteArrayList<String>()
        override fun openFile(path: String) = if (missingHomepage) null else html.byteInputStream().also { paths += path }
        override fun execute(command: String) = WebUiCommandResult(0, "", "")
        override fun spawn(command: String): WebUiProcess = error("Not used")
        override fun listModules() = "[]"
        override fun listPackages(type: String) = "[]"
        override fun getPackagesInfo(packageNamesJson: String) = "[]"
        override fun iconPng(packageName: String, size: Int): ByteArray? = null
    }
}
