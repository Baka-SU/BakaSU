package org.bakasu.bakasu.data.network

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper
import androidx.webkit.WebViewCompat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bakasu.bakasu.data.webui.CompanionBridgeRepository
import org.bakasu.bakasu.data.webui.WearWebUiSession
import org.bakasu.bakasu.domain.model.BridgeException
import org.bakasu.bakasu.domain.model.BridgeFailure

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
        if (target == WearLinkTarget.PHONE) {
            val helper = RemoteActivityHelper(application, java.util.concurrent.Executor { it.run() })
            val availability = withTimeoutOrNull(1_000) { helper.availabilityStatus.first() }
            if (availability == RemoteActivityHelper.STATUS_UNAVAILABLE ||
                availability == RemoteActivityHelper.STATUS_TEMPORARILY_UNAVAILABLE
            ) {
                throw WearLinkException(WearLinkFailure.PHONE_UNAVAILABLE)
            }
            startOnPhone(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE), null)
        }
        target
    }

    /**
     * Opens the WebUI through the app that authorizes this watch, fetching the shared key on first
     * use. False means the phone posted its notification instead of opening the page.
     */
    suspend fun openWebUiOnPhone(moduleId: String, moduleName: String): Boolean = withContext(Dispatchers.IO) {
        var token = ""
        try {
            var peer = bridge.ensureProvisioned()
            token = WearWebUiSession.offer(moduleId, moduleName, peer.address)
            bridge.startServer()
            bridge.awaitServer()
            val response = try {
                bridge.request(peer.address, CompanionBridgeRepository.OPEN) { it.input.readUTF() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // The phone may have revoked or rotated the key; fetch it once more before giving up.
                WearWebUiSession.end(token)
                bridge.forgetSecret()
                peer = runCatching { bridge.provision() }.getOrElse { throw error }
                token = WearWebUiSession.offer(moduleId, moduleName, peer.address)
                bridge.startServer()
                bridge.awaitServer()
                bridge.request(peer.address, CompanionBridgeRepository.OPEN) { it.input.readUTF() }
            }
            when (response) {
                "opened" -> true
                "notification" -> false
                "notifications-required" -> throw BridgeException(BridgeFailure.NOTIFICATIONS)
                else -> throw BridgeException(BridgeFailure.CONNECTION)
            }
        } catch (error: Exception) {
            WearWebUiSession.end(token)
            bridge.stopServer()
            throw error
        }
    }

    /** Starts [intent] on phone [nodeId], or any connected phone when null; a failed dispatch is reported. */
    private suspend fun startOnPhone(intent: Intent, nodeId: String?) {
        val helper = RemoteActivityHelper(application, java.util.concurrent.Executor { it.run() })
        val future = helper.startRemoteActivity(intent, nodeId)
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                future.addListener({
                    try {
                        future.get()
                        if (continuation.isActive) continuation.resume(Unit)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }, java.util.concurrent.Executor { it.run() })
                continuation.invokeOnCancellation { future.cancel(true) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw WearLinkException(WearLinkFailure.PHONE_FAILED, error)
        }
    }
}
