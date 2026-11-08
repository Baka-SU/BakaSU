package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.bakasu.bakasu.data.network.WearLinkException
import org.bakasu.bakasu.data.network.WearLinkFailure
import org.bakasu.bakasu.data.network.WearLinkRepository
import org.bakasu.bakasu.data.network.WearLinkResolution
import org.bakasu.bakasu.data.network.WearLinkTarget
import org.bakasu.bakasu.domain.model.BridgeException
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.bakasu.bakasu.domain.usecase.ConfigureCompanionBridgeUseCase
import org.bakasu.bakasu.domain.usecase.GetStringPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.ObserveCompanionBridgeUseCase

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** What the watch shows itself after a link or module WebUI was resolved. */
sealed interface WearLinkEvent {
    data class WebView(val url: String) : WearLinkEvent
    data class WebUi(val moduleId: String) : WearLinkEvent
}

/** A local link failure, presented separately from the shared phone transfer state. */
sealed interface WearLinkResult {
    /** A null [reason] is an unexpected failure; [onPhone] marks a module WebUI the phone could not open. */
    data class Failed(val reason: WearLinkFailure?, val onPhone: Boolean = false, val bridgeFailure: BridgeFailure? = null, val automaticLink: Boolean = false) : WearLinkResult
}

/**
 * Every entry respects the saved mode. Automatic About links try the phone first.
 */
class WearLinkViewModel(
    private val repository: WearLinkRepository,
    private val getPreference: GetStringPreferenceUseCase,
    observeBridge: ObserveCompanionBridgeUseCase,
    private val configureBridge: ConfigureCompanionBridgeUseCase,
) : ViewModel() {
    private val mutableEvents = MutableSharedFlow<WearLinkEvent>()
    val events = mutableEvents.asSharedFlow()
    private val mutableLoading = MutableStateFlow(false)
    val loading = mutableLoading.asStateFlow()
    private val mutableResult = MutableStateFlow<WearLinkResult?>(null)
    val result = mutableResult.asStateFlow()
    private var task: Job? = null
    private data class WebViewRequest(val url: String, val mode: String, val resolution: WearLinkResolution)
    private var webViewRequest: WebViewRequest? = null
    private var navigatingLink = false
    val transfer = observeBridge.transfer
    val permissionRequest = observeBridge.permissionRequest
    fun answerPermission(id: String, granted: Boolean) = configureBridge.answerPermission(id, granted)
    fun dismissTransfer() = configureBridge.dismissTransfer()

    private val mode get() = getPreference.wear(WearPreference.Link)

    fun open(url: String) = open(url, mode)
    fun openAboutLink(url: String) = open(url, mode, phoneFirst = true)

    /** Internal Browser navigation continues the request; user navigation still cancels it. */
    fun navigateWithinLink(navigate: () -> Unit) {
        navigatingLink = true
        try {
            navigate()
        } finally {
            navigatingLink = false
        }
    }

    fun onNavigation() {
        if (!navigatingLink) cancel()
    }

    /** Continue the original attempt, even if the module preference changed while it was loading. */
    fun webViewFailed(url: String) {
        val request = webViewRequest?.takeIf { it.url == url } ?: return
        if (task?.isActive == true) {
            task?.invokeOnCompletion { error ->
                if (error == null) viewModelScope.launch { webViewFailed(url) }
            }
            return
        }
        webViewRequest = null
        run(automaticLink = request.mode == "auto") {
            showResolution(url, request.mode, repository.continueLink(url, request.resolution.remaining, request.resolution.phoneFailure))
        }
    }

    fun webViewOpened(url: String) {
        if (webViewRequest?.url == url) webViewRequest = null
    }

    private fun open(url: String, mode: String, phoneFirst: Boolean = false) = run(automaticLink = mode == "auto") {
        webViewRequest = null
        showResolution(url, mode, repository.resolve(url, mode, phoneFirst))
    }

    private suspend fun showResolution(url: String, mode: String, resolution: WearLinkResolution) {
        when (resolution.target) {
            WearLinkTarget.WEBVIEW -> {
                webViewRequest = WebViewRequest(url, mode, resolution)
                mutableEvents.emit(WearLinkEvent.WebView(url))
            }

            // The repository has already started the browser.
            WearLinkTarget.BROWSER -> Unit

            WearLinkTarget.PHONE -> Unit
        }
    }

    /**
     * Opens a module WebUI in the watch WebView, or on the phone: when chosen, or automatically when
     * the watch has no WebView. The phone receives the page from this watch over secure RFCOMM.
     */
    fun openWebUi(moduleId: String, moduleName: String) {
        val mode = mode
        if (mode == "phone" || mode != "webview" && !repository.hasWebView()) {
            run(onPhone = true) {
                repository.openWebUiOnPhone(moduleId, moduleName)
            }
        } else if (repository.hasWebView()) {
            viewModelScope.launch { mutableEvents.emit(WearLinkEvent.WebUi(moduleId)) }
        } else {
            fail(WearLinkFailure.WEBVIEW_UNAVAILABLE)
        }
    }

    fun fail(reason: WearLinkFailure?) {
        mutableResult.value = WearLinkResult.Failed(reason)
    }

    fun dismissResult() {
        mutableResult.value = null
    }

    fun cancel() {
        webViewRequest = null
        task?.cancel()
        configureBridge.cancelTransfer()
    }

    /** One opening at a time, shown as loading until it ends; a failure becomes the result. */
    private fun run(onPhone: Boolean = false, automaticLink: Boolean = false, block: suspend () -> Unit) {
        if (task?.isActive == true) return
        task = viewModelScope.launch {
            mutableLoading.value = true
            mutableResult.value = null
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: BridgeException) {
                if (automaticLink) {
                    mutableResult.value = WearLinkResult.Failed(null, bridgeFailure = error.reason, automaticLink = true)
                }
            } catch (error: Exception) {
                val linkError = error as? WearLinkException
                mutableResult.value = WearLinkResult.Failed(linkError?.reason, onPhone, linkError?.phoneFailure, automaticLink)
            } finally {
                mutableLoading.value = false
            }
        }
    }
}
