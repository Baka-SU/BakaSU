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
import org.bakasu.bakasu.data.network.WearLinkTarget
import org.bakasu.bakasu.domain.usecase.GetStringPreferenceUseCase

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** What the watch shows itself after a link or module WebUI was resolved. */
sealed interface WearLinkEvent {
    data class WebView(val url: String) : WearLinkEvent
    data class WebUi(val moduleId: String) : WearLinkEvent
}

/** How opening ended when the phone was involved or it failed; shown once as an overlay. */
sealed interface WearLinkResult {
    data object OpenedOnPhone : WearLinkResult

    /** A null [reason] is an unexpected failure; [onPhone] marks a module WebUI the phone could not open. */
    data class Failed(val reason: WearLinkFailure?, val onPhone: Boolean = false) : WearLinkResult
}

/**
 * Opens links and module WebUI where the saved link mode says: the watch, the phone, or the watch
 * with the phone as fallback. Callers only name what to open.
 */
class WearLinkViewModel(
    private val repository: WearLinkRepository,
    private val getPreference: GetStringPreferenceUseCase,
) : ViewModel() {
    private val mutableEvents = MutableSharedFlow<WearLinkEvent>()
    val events = mutableEvents.asSharedFlow()
    private val mutableLoading = MutableStateFlow(false)
    val loading = mutableLoading.asStateFlow()
    private val mutableResult = MutableStateFlow<WearLinkResult?>(null)
    val result = mutableResult.asStateFlow()
    private var task: Job? = null

    private val mode get() = getPreference.wear(WearPreference.Link)

    fun open(url: String) = open(url, mode)

    /** The watch WebView could not load [url]; in automatic mode the phone takes it instead. */
    fun webViewFailed(url: String) {
        if (mode == "auto") open(url, "phone") else fail(WearLinkFailure.WEBVIEW_UNAVAILABLE)
    }

    private fun open(url: String, mode: String) = run {
        when (repository.resolve(url, mode)) {
            WearLinkTarget.WEBVIEW -> mutableEvents.emit(WearLinkEvent.WebView(url))

            // The repository has already started the browser.
            WearLinkTarget.BROWSER -> Unit

            WearLinkTarget.PHONE -> mutableResult.value = WearLinkResult.OpenedOnPhone
        }
    }

    /**
     * Opens a module WebUI in the watch WebView, or on the phone: when chosen, or automatically when
     * the watch has no WebView. On the phone the page is served by this watch over the Data Layer.
     */
    fun openWebUi(moduleId: String, moduleName: String) {
        val mode = mode
        if (mode == "phone" || mode != "webview" && !repository.hasWebView()) {
            run(onPhone = true) {
                repository.openWebUiOnPhone(moduleId, moduleName)
                mutableResult.value = WearLinkResult.OpenedOnPhone
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
        task?.cancel()
    }

    /** One opening at a time, shown as loading until it ends; a failure becomes the result. */
    private fun run(onPhone: Boolean = false, block: suspend () -> Unit) {
        if (task?.isActive == true) return
        task = viewModelScope.launch {
            mutableLoading.value = true
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableResult.value = WearLinkResult.Failed((error as? WearLinkException)?.reason, onPhone)
            } finally {
                mutableLoading.value = false
            }
        }
    }
}
