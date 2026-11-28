package org.bakasu.bakasu.ui.webui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Window
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import org.bakasu.bakasu.data.packageinfo.InstalledPackageRepository
import org.bakasu.bakasu.data.webui.WebUiBackend
import org.bakasu.bakasu.ui.webui.file.KsuIO
import org.bakasu.bakasu.ui.webui.pm.KsuPm
import org.json.JSONArray
import org.json.JSONObject

@Suppress("unused")
open class WebViewInterface(
    private val state: WebUIState,
    private val backend: WebUiBackend,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val modDir get() = state.modDir

    private fun postToWebView(action: (android.webkit.WebView) -> Unit) {
        mainHandler.post { state.webView?.let(action) }
    }

    @JavascriptInterface
    fun exec(cmd: String): String = backend.execute(cmd).stdout

    @JavascriptInterface
    fun exec(cmd: String, callbackFunc: String) {
        exec(cmd, null, callbackFunc)
    }

    private fun processOptions(sb: StringBuilder, options: String?) {
        val opts = if (options == null) {
            JSONObject()
        } else {
            JSONObject(options)
        }

        val cwd = opts.optString("cwd")
        if (!TextUtils.isEmpty(cwd)) {
            sb.append("cd $cwd;")
        }

        opts.optJSONObject("env")?.let { env ->
            env.keys().forEach { key ->
                sb.append("export $key=${env.getString(key)};")
            }
        }
    }

    @JavascriptInterface
    fun exec(
        cmd: String,
        options: String?,
        callbackFunc: String,
    ) {
        val finalCommand = StringBuilder()
        processOptions(finalCommand, options)
        finalCommand.append(cmd)

        val result = backend.execute(finalCommand.toString())
        val stdout = result.stdout
        val stderr = result.stderr

        val jsCode =
            "javascript: (function() { try { $callbackFunc(${result.code}, ${
                JSONObject.quote(
                    stdout,
                )
            }, ${JSONObject.quote(stderr)}); } catch(e) { console.error(e); } })();"
        postToWebView { it.loadUrl(jsCode) }
    }

    @JavascriptInterface
    fun spawn(command: String, args: String, options: String?, callbackFunc: String) {
        val finalCommand = StringBuilder()

        processOptions(finalCommand, options)

        if (!TextUtils.isEmpty(args)) {
            finalCommand.append(command).append(" ")
            JSONArray(args).let { argsArray ->
                for (i in 0 until argsArray.length()) {
                    finalCommand.append(argsArray.getString(i))
                    finalCommand.append(" ")
                }
            }
        } else {
            finalCommand.append(command)
        }

        val emitData = fun(name: String, data: String) {
            val jsCode =
                "javascript: (function() { try { $callbackFunc.$name.emit('data', ${
                    JSONObject.quote(
                        data,
                    )
                }); } catch(e) { console.error('emitData', e); } })();"
            postToWebView { it.loadUrl(jsCode) }
        }

        val process = backend.spawn(finalCommand.toString())
        process.start(
            onStdout = { emitData("stdout", it) },
            onStderr = { emitData("stderr", it) },
            onComplete = { result ->
                val emitExitCode =
                    "javascript: (function() { try { $callbackFunc.emit('exit', ${result.code}); } catch(e) { console.error(`emitExit error: \${e}`); } })();"
                postToWebView { it.loadUrl(emitExitCode) }

                if (result.code != 0) {
                    val emitErrCode =
                        "javascript: (function() { try { var err = new Error(); err.exitCode = ${result.code}; err.message = ${
                            JSONObject.quote(
                                result.stderr,
                            )
                        };$callbackFunc.emit('error', err); } catch(e) { console.error('emitErr', e); } })();"
                    postToWebView { it.loadUrl(emitErrCode) }
                }
            },
        )
    }

    @JavascriptInterface
    fun toast(msg: String) {
        postToWebView { view ->
            Toast.makeText(view.context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun fullScreen(enable: Boolean) {
        postToWebView { view ->
            val context = view.context
            if (context is Activity) {
                if (enable) {
                    hideSystemUI(context.window)
                } else {
                    showSystemUI(context.window)
                }
            }
            enableEdgeToEdge(enable)
        }
    }

    @JavascriptInterface
    fun enableInsets(enable: Boolean = true) {
        enableEdgeToEdge(enable)
    }

    @JavascriptInterface
    fun enableEdgeToEdge(enable: Boolean = true) {
        state.isInsetsEnabled = enable
    }

    @JavascriptInterface
    fun moduleInfo(): String {
        val moduleInfos = JSONArray(backend.listModules())
        val currentModuleInfo = JSONObject()
        currentModuleInfo.put("moduleDir", modDir)
        val moduleId = File(modDir).name
        for (i in 0 until moduleInfos.length()) {
            val currentInfo = moduleInfos.getJSONObject(i)

            if (currentInfo.getString("id") != moduleId) {
                continue
            }

            val keys = currentInfo.keys()
            for (key in keys) {
                currentModuleInfo.put(key, currentInfo.get(key))
            }
            break
        }
        return currentModuleInfo.toString()
    }

    @Deprecated("Use ksu.pm().listPackages() instead")
    @JavascriptInterface
    fun listPackages(type: String): String = backend.listPackages(type)

    @Deprecated("Use ksu.pm().getPackagesInfo() instead")
    @JavascriptInterface
    fun getPackagesInfo(packageNamesJson: String): String = backend.getPackagesInfo(packageNamesJson)

    @JavascriptInterface
    fun exit() {
        state.requestExit()
    }

    open fun destroy() = Unit
}

class LocalWebViewInterface(
    state: WebUIState,
    backend: WebUiBackend,
    private val packageRepository: InstalledPackageRepository,
) : WebViewInterface(state, backend) {
    @JavascriptInterface
    fun io() = KsuIO

    @JavascriptInterface
    fun pm() = KsuPm(packageRepository)

    override fun destroy() {
        KsuIO.destroy()
    }
}

fun hideSystemUI(window: Window) = WindowInsetsControllerCompat(window, window.decorView).let { controller ->
    controller.hide(WindowInsetsCompat.Type.systemBars())
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}

fun showSystemUI(window: Window) = WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
