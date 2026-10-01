package com.resukisu.resukisu.data.network

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.webkit.WebViewCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class WearLinkTarget { WEBVIEW, PHONE }

/** Why a link could not be opened, so the UI can show the actual reason and offer a mode switch. */
enum class WearLinkFailure { UNSUPPORTED_URL, WEBVIEW_UNAVAILABLE, PHONE_UNAVAILABLE, PHONE_FAILED }
class WearLinkException(val reason: WearLinkFailure, cause: Throwable? = null) : Exception(reason.name, cause)

/**
 * Links open in the watch's own WebView or are sent to the phone. Watches may ship without WebView,
 * and handing links to an arbitrary watch browser is not offered.
 */
class WearLinkRepository(private val application: Application) {
    // Both the system feature and an installed provider are required; builds that remove WebView
    // drop the feature, and a provider package alone does not prove WebView can be created.
    fun hasWebView(): Boolean = application.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW) &&
        runCatching { WebViewCompat.getCurrentWebViewPackage(application) != null }.getOrDefault(false)

    suspend fun resolve(url: String, mode: String): WearLinkTarget = withContext(Dispatchers.IO) {
        val uri = Uri.parse(url)
        if (uri.scheme !in listOf("https", "http")) throw WearLinkException(WearLinkFailure.UNSUPPORTED_URL)
        val hasWebView = hasWebView()
        val target = when (mode) {
            "webview" -> if (hasWebView) WearLinkTarget.WEBVIEW else throw WearLinkException(WearLinkFailure.WEBVIEW_UNAVAILABLE)
            "phone" -> WearLinkTarget.PHONE
            // Automatic: the watch WebView when present, otherwise the phone.
            else -> if (hasWebView) WearLinkTarget.WEBVIEW else WearLinkTarget.PHONE
        }
        if (target == WearLinkTarget.PHONE) {
            val helper = RemoteActivityHelper(application, java.util.concurrent.Executor { it.run() })
            val availability = withTimeoutOrNull(1_000) { helper.availabilityStatus.first() }
            if (availability == RemoteActivityHelper.STATUS_UNAVAILABLE ||
                availability == RemoteActivityHelper.STATUS_TEMPORARILY_UNAVAILABLE) {
                throw WearLinkException(WearLinkFailure.PHONE_UNAVAILABLE)
            }
            // The helper resolves connected target nodes and reports a failed remote dispatch.
            val future = helper.startRemoteActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    future.addListener({
                        try { future.get(); if (continuation.isActive) continuation.resume(Unit) }
                        catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                    }, java.util.concurrent.Executor { it.run() })
                    continuation.invokeOnCancellation { future.cancel(true) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw WearLinkException(WearLinkFailure.PHONE_FAILED, error)
            }
        }
        target
    }
}
