package org.bakasu.bakasu.data.network

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.data.webui.BridgeHandoff
import org.bakasu.bakasu.data.webui.CompanionBridgeRepository
import org.bakasu.bakasu.domain.model.BridgeAction
import org.bakasu.bakasu.domain.model.BridgeException
import org.bakasu.bakasu.domain.model.BridgeFailure

enum class WearLinkTarget { WEBVIEW, BROWSER, PHONE }

enum class WearLinkFailure { UNSUPPORTED_URL, WEBVIEW_UNAVAILABLE, BROWSER_FAILED, PHONE_UNAVAILABLE, PHONE_APP_MISSING, PHONE_FAILED }
class WearLinkException(val reason: WearLinkFailure, cause: Throwable? = null, val phoneFailure: BridgeFailure? = null) : Exception(reason.name, cause)

/** Remaining handlers belong to this attempt, including an asynchronous WebView failure. */
data class WearLinkResolution(val target: WearLinkTarget, val remaining: List<WearLinkTarget>, val phoneFailure: BridgeFailure? = null)

internal fun wearLinkTargets(mode: String, phoneFirst: Boolean): List<WearLinkTarget> = when (mode) {
    "phone" -> listOf(WearLinkTarget.PHONE)

    "webview" -> listOf(WearLinkTarget.BROWSER, WearLinkTarget.WEBVIEW)

    else -> if (phoneFirst) {
        listOf(WearLinkTarget.PHONE, WearLinkTarget.WEBVIEW, WearLinkTarget.BROWSER)
    } else {
        listOf(WearLinkTarget.BROWSER, WearLinkTarget.WEBVIEW, WearLinkTarget.PHONE)
    }
}

internal suspend fun resolveWearLink(
    targets: List<WearLinkTarget>,
    previousPhoneFailure: BridgeFailure? = null,
    open: suspend (WearLinkTarget) -> Unit,
): WearLinkResolution {
    var phoneFailure = previousPhoneFailure
    var lastFailure = WearLinkFailure.WEBVIEW_UNAVAILABLE
    for ((index, target) in targets.withIndex()) {
        try {
            open(target)
            return WearLinkResolution(target, targets.drop(index + 1), phoneFailure)
        } catch (error: BridgeException) {
            if (error.reason in setOf(BridgeFailure.PERMISSION, BridgeFailure.REJECTED, BridgeFailure.RESULT_UNKNOWN)) throw error
            phoneFailure = error.reason
            if (target == targets.last()) throw error
        } catch (error: WearLinkException) {
            lastFailure = error.reason
        }
    }
    throw WearLinkException(lastFailure, phoneFailure = phoneFailure)
}

/** Explicit device choices limit the handlers; only automatic mode can cross devices. */
class WearLinkRepository(private val application: Application, private val bridge: CompanionBridgeRepository) {
    fun openBrowser(url: String): WearLinkTarget {
        if (!BridgeHandoff.valid(BridgeAction.LINK, url)) throw WearLinkException(WearLinkFailure.UNSUPPORTED_URL)
        val uri = Uri.parse(url)
        if (!hasBrowser(uri)) throw WearLinkException(WearLinkFailure.BROWSER_FAILED)
        try {
            application.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (error: Exception) {
            throw WearLinkException(WearLinkFailure.BROWSER_FAILED, error)
        }
        return WearLinkTarget.BROWSER
    }

    fun hasWebView(): Boolean = application.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW) &&
        runCatching { WebViewCompat.getCurrentWebViewPackage(application) != null }.getOrDefault(false)

    private fun hasBrowser(uri: Uri): Boolean {
        val view = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        return application.packageManager.queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY).any {
            it.activityInfo.exported && it.activityInfo.packageName != "android" && it.activityInfo.packageName != application.packageName
        }
    }

    suspend fun resolve(url: String, mode: String, phoneFirst: Boolean = false): WearLinkResolution = continueLink(url, wearLinkTargets(mode, phoneFirst), reportPhoneFailure = mode == "phone")

    suspend fun continueLink(
        url: String,
        targets: List<WearLinkTarget>,
        phoneFailure: BridgeFailure? = null,
        reportPhoneFailure: Boolean = false,
    ): WearLinkResolution = withContext(Dispatchers.IO) {
        if (!BridgeHandoff.valid(BridgeAction.LINK, url)) throw WearLinkException(WearLinkFailure.UNSUPPORTED_URL)
        resolveWearLink(targets, phoneFailure) { target ->
            when (target) {
                WearLinkTarget.BROWSER -> openBrowser(url)
                WearLinkTarget.WEBVIEW -> if (!hasWebView()) throw WearLinkException(WearLinkFailure.WEBVIEW_UNAVAILABLE)
                WearLinkTarget.PHONE -> bridge.handoff(BridgeAction.LINK, url, reportFailure = reportPhoneFailure)
            }
        }
    }

    suspend fun openWebUiOnPhone(moduleId: String, moduleName: String) {
        bridge.handoff(BridgeAction.WEB_UI, moduleId, moduleName)
    }
}
