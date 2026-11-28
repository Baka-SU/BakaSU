package org.bakasu.bakasu.ui.webui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.AppSettingsRepository
import org.bakasu.bakasu.data.packageinfo.AppIconDataSource
import org.bakasu.bakasu.data.packageinfo.InstalledPackageRepository
import org.bakasu.bakasu.data.webui.LocalWebUiBackend
import org.bakasu.bakasu.data.webui.WebUiBackend
import org.bakasu.bakasu.data.webui.WebUiRepository
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiAction
import org.bakasu.bakasu.ui.viewmodel.SuperUserViewModel

private const val INTERNAL_BLOB_DOWNLOAD_HOST = "blob-download.kernelsu.internal"

private fun loadDownloadJs(context: Context): String = context.assets.open("webview/download.js").bufferedReader(Charsets.UTF_8).use { it.readText() }

fun Activity.setTaskDescription(label: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("DEPRECATION")
        setTaskDescription(ActivityManager.TaskDescription(label))
    } else {
        val taskDescription = ActivityManager.TaskDescription.Builder()
            .setLabel(label)
            .build()
        setTaskDescription(taskDescription)
    }
}

@SuppressLint("SetJavaScriptEnabled")
suspend fun prepareWebView(
    activity: Activity,
    moduleId: String,
    webUIState: WebUIState,
    superUserViewModel: SuperUserViewModel,
    settingsRepository: AppSettingsRepository,
    packageRepository: InstalledPackageRepository,
    appIconDataSource: AppIconDataSource,
    webUiRepository: WebUiRepository,
    colorsCssProvider: () -> String,
    recoverFromRenderCrash: Boolean = false,
    webViewFactory: (Activity) -> WebView = ::WebView,
) {
    withContext(Dispatchers.IO) {
        val moduleInfo = try {
            webUiRepository.getModuleInfo(moduleId)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            withContext(Dispatchers.Main) {
                webUIState.uiEvent = WebUIEvent.Error(
                    activity.getString(R.string.module_unavailable, moduleId),
                )
            }
            return@withContext
        }

        if (moduleInfo == null) {
            withContext(Dispatchers.Main) {
                webUIState.uiEvent = WebUIEvent.Error(activity.getString(R.string.no_such_module, moduleId))
            }
            return@withContext
        }

        if (!moduleInfo.hasWebUi || !moduleInfo.enabled || moduleInfo.remove) {
            withContext(Dispatchers.Main) {
                webUIState.uiEvent = WebUIEvent.Error(activity.getString(R.string.module_unavailable, moduleInfo.name))
            }
            return@withContext
        }

        if (packageRepository.packages.value.isEmpty()) {
            superUserViewModel.dispatch(SuperUserUiAction.Refresh)
        }
        withContext(Dispatchers.Main) {
            webUIState.moduleName = moduleInfo.name
            webUIState.modDir = "/data/adb/modules/$moduleId"
            val backend = LocalWebUiBackend(webUiRepository, packageRepository, appIconDataSource)
            createWebView(
                activity,
                webUIState,
                backend,
                settingsRepository,
                colorsCssProvider = colorsCssProvider,
                recoverFromRenderCrash = recoverFromRenderCrash,
                webViewFactory = webViewFactory,
                webviewInterface = LocalWebViewInterface(webUIState, backend, packageRepository),
            )
        }
    }
}

private fun Uri.isModuleOrigin() = scheme == "https" && host == MODULE_HOST ||
    scheme.equals("ksu", ignoreCase = true) && host.equals("icon", ignoreCase = true)

private const val MODULE_HOST = "mui.kernelsu.org"
private const val TAG = "ModuleWebUI"

/**
 * Creates a module WebView using [backend] for files, commands and the `ksu` bridge.
 * Call on the main thread after setting the module name and directory in [webUIState].
 *
 * [restrictToModule] keeps other origins out of the bridge-enabled page and opens external links
 * in the browser. [recoverFromRenderCrash] closes a failed renderer with an error.
 */
@SuppressLint("SetJavaScriptEnabled")
fun createWebView(
    activity: Activity,
    webUIState: WebUIState,
    backend: WebUiBackend,
    settingsRepository: AppSettingsRepository,
    restrictToModule: Boolean = false,
    colorsCssProvider: () -> String,
    recoverFromRenderCrash: Boolean = false,
    onPageLoaded: (() -> Unit)? = null,
    webViewFactory: (Activity) -> WebView = ::WebView,
    webviewInterface: WebViewInterface = WebViewInterface(webUIState, backend),
) {
    activity.setTaskDescription("KernelSU - ${webUIState.moduleName}")

    val webView = webViewFactory(activity)
    Log.i(TAG, "Opening ${webUIState.modDir}, remote=$restrictToModule, WebView=${WebView.getCurrentWebViewPackage()?.versionName}")
    webView.setBackgroundColor(Color.TRANSPARENT)

    WebView.setWebContentsDebuggingEnabled(
        settingsRepository.getBoolean("enable_web_debugging", false),
    )

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
    }

    val webRoot = File("${webUIState.modDir}/webroot")
    val webViewAssetLoader = WebViewAssetLoader.Builder()
        .setDomain(MODULE_HOST)
        .addPathHandler(
            "/",
            SuFilePathHandler(
                webRoot,
                backend,
                { webUIState.currentInsets },
                { enable -> webUIState.isInsetsEnabled = enable },
                colorsCssProvider,
            ),
        )
        .build()

    // WebViewClient
    webView.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            webUIState.pageLoaded = false
            Log.i(TAG, "Page started: $url")
        }

        override fun onPageFinished(view: WebView, url: String?) {
            webUIState.pageLoaded = true
            Log.i(TAG, "Page finished: $url")
            if (webUIState.uiEvent !is WebUIEvent.Error) onPageLoaded?.invoke()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            Log.e(TAG, "Resource failed: ${request.url}, mainFrame=${request.isForMainFrame}, code=${error.errorCode}, ${error.description}")
            if (restrictToModule && request.isForMainFrame) {
                webUIState.uiEvent = WebUIEvent.Error(activity.getString(R.string.wear_webui_load_failed))
            }
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            Log.e(TAG, "HTTP ${errorResponse.statusCode}: ${request.url}, mainFrame=${request.isForMainFrame}")
            if (restrictToModule && request.isForMainFrame) {
                webUIState.uiEvent = WebUIEvent.Error(activity.getString(R.string.wear_webui_load_failed))
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.e(TAG, "Renderer exited: crashed=${detail.didCrash()}")
            if (!recoverFromRenderCrash) {
                return super.onRenderProcessGone(view, detail)
            }
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            webUIState.webView = null
            webUIState.uiEvent = WebUIEvent.Error(activity.getString(R.string.operation_failed))
            return true
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url
            BlobDownloadHandler.shouldInterceptRequest(request)?.let { return it }
            if (url.scheme.equals("ksu", ignoreCase = true) && url.host.equals("icon", ignoreCase = true)) {
                val packageName = url.path?.substring(1)
                if (!packageName.isNullOrEmpty()) {
                    val icon = backend.iconPng(packageName, 512)
                    if (icon != null) {
                        return WebResourceResponse(
                            "image/png",
                            null,
                            200,
                            "OK",
                            mapOf("Access-Control-Allow-Origin" to "*"),
                            ByteArrayInputStream(icon),
                        )
                    }
                }
            }
            return webViewAssetLoader.shouldInterceptRequest(url) ?: if (restrictToModule && !url.isModuleOrigin()) {
                WebResourceResponse("text/plain", null, 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            } else {
                null
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!restrictToModule || request.url.isModuleOrigin()) return false
            if (request.url.scheme == "https" && request.url.host == INTERNAL_BLOB_DOWNLOAD_HOST) return false
            if (request.isForMainFrame && request.url.scheme in listOf("https", "http")) {
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
            }
            return true
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            webUIState.webCanGoBack = view?.canGoBack() ?: false
            if (webUIState.isInsetsEnabled) webUIState.webView?.evaluateJavascript(webUIState.currentInsets.js, null)
            view?.evaluateJavascript(loadDownloadJs(activity), null)
            super.doUpdateVisitedHistory(view, url, isReload)
        }
    }

    // WebChromeClient
    webView.webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            val priority = when (message.messageLevel()) {
                ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
                ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                ConsoleMessage.MessageLevel.DEBUG -> Log.DEBUG
                else -> Log.INFO
            }
            Log.println(priority, TAG, "${message.sourceId()}:${message.lineNumber()}: ${message.message()}")
            return true
        }

        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            if (message == null || result == null) return false
            webUIState.uiEvent = WebUIEvent.ShowAlert(message, result)
            return true
        }

        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            if (message == null || result == null) return false
            webUIState.uiEvent = WebUIEvent.ShowConfirm(message, result)
            return true
        }

        override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
            if (message == null || result == null || defaultValue == null) return false
            webUIState.uiEvent = WebUIEvent.ShowPrompt(message, defaultValue, result)
            return true
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            webUIState.filePathCallback?.onReceiveValue(null)
            webUIState.filePathCallback = filePathCallback

            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
            if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            webUIState.uiEvent = WebUIEvent.ShowFileChooser(intent)
            return true
        }
    }

    // JS Interface
    val downloadInterface = WebUIDownloadInterface(webUIState)
    webUIState.webViewInterface = webviewInterface
    webUIState.downloadInterface = downloadInterface
    webUIState.webView = webView
    webView.addJavascriptInterface(webviewInterface, "ksu")
    webView.addJavascriptInterface(downloadInterface, "ksu_download")
    webView.setDownloadListener { url, _, _, _, _ ->
        if (Uri.parse(url).host != INTERNAL_BLOB_DOWNLOAD_HOST) {
            downloadInterface.openExternal(url)
        }
    }
    webView.evaluateJavascript(loadDownloadJs(activity), null)
    webUIState.uiEvent = WebUIEvent.WebViewReady
}

@Composable
fun HandleWebViewLifecycle(webUIState: WebUIState) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, webUIState) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webUIState.webView?.onResume()
                Lifecycle.Event.ON_PAUSE -> webUIState.webView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
fun HandleConfigurationChanges(webUIState: WebUIState) {
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.fontScale, webUIState.webView) {
        webUIState.webView?.settings?.textZoom = (configuration.fontScale * 100).toInt()
    }
}
