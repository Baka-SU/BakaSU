package org.bakasu.bakasu.ui.webui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.util.concurrent.CopyOnWriteArrayList
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.AppSettingsRepository
import org.bakasu.bakasu.data.webui.WebUiBackend
import org.bakasu.bakasu.domain.model.WebUiCommandResult
import org.bakasu.bakasu.domain.model.WebUiProcess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

class RemoteWebUiRenderingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun phoneLoadsWatchModuleWithoutLocalModuleFilesAndOnlyThenAcknowledges() {
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
        } finally {
            compose.runOnIdle { state.dispose() }
        }
    }

    @Test
    fun missingWatchHomepageReportsFailureWithoutAcknowledgingSuccess() {
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

    private class TestBackend(private val missingHomepage: Boolean = false) : WebUiBackend {
        val paths = CopyOnWriteArrayList<String>()
        override fun openFile(path: String) = if (missingHomepage) null else "<html><body>Watch module</body></html>".byteInputStream().also { paths += path }
        override fun execute(command: String) = WebUiCommandResult(0, "", "")
        override fun spawn(command: String): WebUiProcess = error("Not used")
        override fun listModules() = "[]"
        override fun listPackages(type: String) = "[]"
        override fun getPackagesInfo(packageNamesJson: String) = "[]"
        override fun iconPng(packageName: String, size: Int): ByteArray? = null
    }
}
