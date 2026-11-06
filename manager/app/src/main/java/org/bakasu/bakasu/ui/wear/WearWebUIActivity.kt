package org.bakasu.bakasu.ui.wear

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.AppSettingsRepository
import org.bakasu.bakasu.data.packageinfo.AppIconDataSource
import org.bakasu.bakasu.data.packageinfo.InstalledPackageRepository
import org.bakasu.bakasu.data.webui.WebUiRepository
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.viewmodel.SuperUserViewModel
import org.bakasu.bakasu.ui.wear.component.WearBackground
import org.bakasu.bakasu.ui.wear.component.WearDialogIcon
import org.bakasu.bakasu.ui.wear.component.WearSubPage
import org.bakasu.bakasu.ui.wear.component.WearSwipeToDismissBox
import org.bakasu.bakasu.ui.wear.component.WearTextInputPage
import org.bakasu.bakasu.ui.wear.component.WearWebView
import org.bakasu.bakasu.ui.webui.HandleConfigurationChanges
import org.bakasu.bakasu.ui.webui.HandleWebViewLifecycle
import org.bakasu.bakasu.ui.webui.MonetColorsProvider
import org.bakasu.bakasu.ui.webui.WebUIEvent
import org.bakasu.bakasu.ui.webui.WebUIState
import org.bakasu.bakasu.ui.webui.prepareWebView
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val EXTRA_ID = "id"

/** A module WebUI on the watch: the phone's WebView setup and `ksu` bridge in a watch surface. */
class WearWebUIActivity : WearActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearManagerTheme { WearWebUIContent(this) { finish() } }
        }
    }

    companion object {
        /** Opens the WebUI of [moduleId] on the watch; the data URI keeps one task per module. */
        fun intent(context: Context, moduleId: String): Intent = Intent(context, WearWebUIActivity::class.java)
            .setData("kernelsu://webui/$moduleId".toUri())
            .putExtra(EXTRA_ID, moduleId)
    }
}

@Composable
private fun WearWebUIContent(activity: ComponentActivity, onFinish: () -> Unit) {
    val moduleId = remember { activity.intent.getStringExtra(EXTRA_ID) }
    val webUIState = remember { WebUIState() }
    val superUserViewModel = koinViewModel<SuperUserViewModel>()
    val settingsRepository = koinInject<AppSettingsRepository>()
    val packageRepository = koinInject<InstalledPackageRepository>()
    val appIconDataSource = koinInject<AppIconDataSource>()
    val webUiRepository = koinInject<WebUiRepository>()
    val colorsCss = koinInject<MonetColorsProvider>().getColorsCss()
    val currentColorsCss = rememberUpdatedState(colorsCss)

    LaunchedEffect(moduleId) {
        if (moduleId == null) {
            onFinish()
            return@LaunchedEffect
        }
        prepareWebView(
            activity,
            moduleId,
            webUIState,
            superUserViewModel,
            settingsRepository,
            packageRepository,
            appIconDataSource,
            webUiRepository,
            { currentColorsCss.value },
            recoverFromRenderCrash = true,
        )
    }
    DisposableEffect(Unit) {
        onDispose { webUIState.dispose() }
    }
    when (val event = webUIState.uiEvent) {
        is WebUIEvent.Error -> LaunchedEffect(event) {
            Toast.makeText(activity, event.message, Toast.LENGTH_SHORT).show()
            onFinish()
        }

        is WebUIEvent.Close -> LaunchedEffect(event) { onFinish() }

        else -> Unit
    }
    WearWebUIScreen(webUIState, onFinish)
}

@Composable
private fun WearWebUIScreen(state: WebUIState, onFinish: () -> Unit) {
    val back: () -> Unit = { if (state.webCanGoBack) state.webView?.goBack() else onFinish() }
    BackHandler { back() }
    AppScaffold(timeText = {}, containerColor = Color.Transparent) {
        WearSwipeToDismissBox(onDismissed = back) { background ->
            if (!background) {
                when (val event = state.uiEvent) {
                    // A waiting spinner; the branded loading screen is only shown at app startup.
                    WebUIEvent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    else -> Box(Modifier.fillMaxSize()) {
                        // Keep the WebView attached while a prompt is open, including its history,
                        // file result launcher and lifecycle observer.
                        WearModuleWebView(state, back)
                        // Pages over the WebView take the opaque app backdrop so it does not show through.
                        if (!state.pageLoaded) {
                            WearBackground {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        if (event is WebUIEvent.ShowPrompt) {
                            WearSubPage({ state.onPromptResult(null) }) {
                                WearBackground { WearTextInputPage(event.message, event.defaultValue) { state.onPromptResult(it) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Keep module loading and its JS/file/lifecycle handlers on the shared full-window watch surface. */
@Composable
private fun WearModuleWebView(state: WebUIState, onBack: () -> Unit) {
    val webView = state.webView ?: return
    DisposableEffect(webView) {
        val listener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            if (view.width > 0 && view.height > 0 && !state.isUrlLoaded) {
                webView.loadUrl("https://mui.kernelsu.org/index.html")
                state.isUrlLoaded = true
            }
        }
        webView.addOnLayoutChangeListener(listener)
        onDispose { webView.removeOnLayoutChangeListener(listener) }
    }
    WearWebView(webView, onBack)
    HandleWearWebUIEvent(state)
    HandleWebViewLifecycle(state)
    HandleConfigurationChanges(state)
}

@Composable
private fun HandleWearWebUIEvent(state: WebUIState) {
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uris: Array<Uri>? = if (result.resultCode != Activity.RESULT_OK || data == null) {
            null
        } else {
            data.clipData?.let { clip -> Array(clip.itemCount) { clip.getItemAt(it).uri } }
                ?: data.data?.let { arrayOf(it) }
        }
        state.onFileChooserResult(uris)
    }
    val event = state.uiEvent
    if (event is WebUIEvent.ShowFileChooser) {
        LaunchedEffect(event) {
            runCatching { fileLauncher.launch(event.intent) }.onFailure { state.onFileChooserResult(null) }
        }
        return
    }
    if (event !is WebUIEvent.ShowAlert && event !is WebUIEvent.ShowConfirm) return
    val message = when (event) {
        is WebUIEvent.ShowAlert -> event.message
        is WebUIEvent.ShowConfirm -> event.message
    }
    val title = stringResource(R.string.module_webui_alert, state.moduleName)
    val dialog = rememberCustomDialog { dismiss ->
        fun respond(confirmed: Boolean) {
            if (event is WebUIEvent.ShowAlert) state.onAlertResult() else state.onConfirmResult(confirmed)
            dismiss()
        }
        AlertDialog(
            visible = true,
            onDismissRequest = { respond(false) },
            icon = { WearDialogIcon(Icons.Rounded.Info) },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = { respond(true) }) },
            dismissButton = { AlertDialogDefaults.DismissButton(onClick = { respond(false) }) },
        )
    }
    LaunchedEffect(event) { dialog.show() }
}
