package org.bakasu.bakasu.ui.webui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.AppSettingsRepository
import org.bakasu.bakasu.data.webui.BridgeActivityLaunch
import org.bakasu.bakasu.data.webui.CompanionBridgeRepository
import org.bakasu.bakasu.data.webui.RemoteWebUiBackend
import org.bakasu.bakasu.ui.theme.KernelSUTheme
import org.koin.compose.koinInject

/**
 * The phone side of a watch module's WebUI, opened internally after Bluetooth app authentication.
 * The activity claims the module session from the explicitly paired watch over secure RFCOMM. The
 * page, its files and its `ksu` bridge commands are all served by the watch, so every change the
 * page makes is applied on the watch; closing the page tells the watch to end the session and
 * reload its modules.
 */
class RemoteWebUIActivity : ComponentActivity() {
    private var backend: RemoteWebUiBackend? = null

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        super.onCreate(savedInstanceState)

        val peer = intent.getStringExtra(BridgeActivityLaunch.PEER)
        if (peer == null) {
            finish()
            return
        }

        setContent {
            KernelSUTheme {
                val webUIState = remember { WebUIState() }
                val settingsRepository = koinInject<AppSettingsRepository>()
                val transport = koinInject<CompanionBridgeRepository>()
                val colorsCss = koinInject<MonetColorsProvider>().getColorsCss()
                val currentColorsCss = rememberUpdatedState(colorsCss)
                val claimFailed = stringResource(R.string.operation_failed)
                LaunchedEffect(Unit) {
                    val remote = withContext(Dispatchers.IO) { RemoteWebUiBackend.claim(transport, peer) }
                    if (remote == null) {
                        BridgeActivityLaunch.failed(intent.getStringExtra(BridgeActivityLaunch.REQUEST))
                        webUIState.uiEvent = WebUIEvent.Error(claimFailed)
                        return@LaunchedEffect
                    }
                    backend = remote
                    webUIState.moduleName = remote.moduleName
                    webUIState.modDir = "/data/adb/modules/${remote.moduleId}"
                    createWebView(
                        this@RemoteWebUIActivity,
                        webUIState,
                        remote,
                        settingsRepository,
                        restrictToModule = true,
                        colorsCssProvider = { currentColorsCss.value },
                    )
                    BridgeActivityLaunch.ready(intent.getStringExtra(BridgeActivityLaunch.REQUEST))
                }
                DisposableEffect(Unit) { onDispose { webUIState.dispose() } }

                when (val event = webUIState.uiEvent) {
                    is WebUIEvent.Error -> LaunchedEffect(event) {
                        Toast.makeText(this@RemoteWebUIActivity, event.message, Toast.LENGTH_SHORT).show()
                        finish()
                    }

                    is WebUIEvent.Close -> LaunchedEffect(event) { finish() }

                    else -> Unit
                }
                Crossfade(targetState = webUIState.uiEvent is WebUIEvent.Loading, animationSpec = tween(300)) { loading ->
                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    } else {
                        WebUIScreen(webUIState = webUIState)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) backend?.close()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra(BridgeActivityLaunch.REQUEST) == this.intent.getStringExtra(BridgeActivityLaunch.REQUEST)) {
            // A late notification brings the already claimed page forward without claiming twice.
            if (backend != null) BridgeActivityLaunch.ready(intent.getStringExtra(BridgeActivityLaunch.REQUEST))
            return
        }
        backend?.close()
        backend = null
        setIntent(intent)
        recreate()
    }
}
