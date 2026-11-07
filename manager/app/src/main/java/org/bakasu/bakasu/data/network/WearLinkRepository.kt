package org.bakasu.bakasu.data.network

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.data.webui.CompanionBridgeRepository
import org.bakasu.bakasu.domain.model.BridgeAction

enum class WearLinkTarget { WEBVIEW, BROWSER, PHONE }

/** Why a link could not be opened, so the UI can show the actual reason and offer a mode switch. */
enum class WearLinkFailure { UNSUPPORTED_URL, WEBVIEW_UNAVAILABLE, BROWSER_FAILED, PHONE_UNAVAILABLE, PHONE_APP_MISSING, PHONE_FAILED }
class WearLinkException(val reason: WearLinkFailure, cause: Throwable? = null) : Exception(reason.name, cause)

/**
 * Links open on the watch or are sent to the phone. On the watch they go to the default browser
 * when one can handle the link, otherwise to the app's WebView, which some watches ship without.
 */
class WearLinkRepository(private val application: Application, private val bridge: CompanionBridgeRepository) {
    // Both the system feature and an installed provider are required; builds that remove WebView
    // drop the feature, and a provider package alone does not prove WebView can be created.
    fun hasWebView(): Boolean = application.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW) &&
        runCatching { WebViewCompat.getCurrentWebViewPackage(application) != null }.getOrDefault(false)

    /** Includes available browsers when the watch has not selected a default handler yet. */
    private fun hasBrowser(uri: Uri): Boolean {
        val view = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        return application.packageManager.queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY).any {
            it.activityInfo.exported && it.activityInfo.packageName != "android" && it.activityInfo.packageName != application.packageName
        }
    }

    suspend fun resolve(url: String, mode: String): WearLinkTarget = withContext(Dispatchers.IO) {
        val uri = Uri.parse(url)
        if (uri.scheme !in listOf("https", "http")) throw WearLinkException(WearLinkFailure.UNSUPPORTED_URL)
        val hasWebView = hasWebView()
        val target = when (mode) {
            // On the watch: a browser when available, whether or not WebView exists.
            "webview" -> if (hasBrowser(uri)) {
                WearLinkTarget.BROWSER
            } else if (hasWebView) {
                WearLinkTarget.WEBVIEW
            } else {
                throw WearLinkException(WearLinkFailure.WEBVIEW_UNAVAILABLE)
            }

            "phone" -> WearLinkTarget.PHONE

            // Automatic uses local handlers before requiring a paired phone.
            else -> when {
                hasBrowser(uri) -> WearLinkTarget.BROWSER
                hasWebView -> WearLinkTarget.WEBVIEW
                else -> WearLinkTarget.PHONE
            }
        }
        if (target == WearLinkTarget.BROWSER) {
            runCatching {
                application.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { throw WearLinkException(WearLinkFailure.BROWSER_FAILED, it) }
        }
        if (target == WearLinkTarget.PHONE) bridge.handoff(BridgeAction.LINK, url)
        target
    }

    suspend fun openWebUiOnPhone(moduleId: String, moduleName: String) {
        bridge.handoff(BridgeAction.WEB_UI, moduleId, moduleName)
    }
}
