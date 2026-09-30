package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.data.network.WearLinkException
import com.resukisu.resukisu.data.network.WearLinkFailure
import com.resukisu.resukisu.data.network.WearLinkRepository
import com.resukisu.resukisu.data.network.WearLinkTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed interface WearLinkEvent {
    data class WebView(val url: String) : WearLinkEvent
    data class WebUi(val moduleId: String, val moduleName: String) : WearLinkEvent
    data object SentToPhone : WearLinkEvent
    /** A null [reason] is an unexpected failure. [webUi] marks failures of the module WebUI flow. */
    data class Failed(val reason: WearLinkFailure?, val webUi: Boolean = false) : WearLinkEvent
}
class WearLinkViewModel(private val repository: WearLinkRepository) : ViewModel() {
    private val mutableEvents = MutableSharedFlow<WearLinkEvent>()
    val events = mutableEvents.asSharedFlow()
    private val mutableLoading = MutableStateFlow(false)
    val loading = mutableLoading.asStateFlow()
    private var task: Job? = null
    fun open(url: String, mode: String) {
        if (task?.isActive == true) return
        task = viewModelScope.launch {
            mutableLoading.value = true
            try { when (repository.resolve(url, mode)) {
                WearLinkTarget.WEBVIEW -> mutableEvents.emit(WearLinkEvent.WebView(url))
                WearLinkTarget.PHONE -> mutableEvents.emit(WearLinkEvent.SentToPhone)
                WearLinkTarget.BROWSER -> Unit
            } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutableEvents.emit(WearLinkEvent.Failed((error as? WearLinkException)?.reason)) }
            finally { mutableLoading.value = false }
        }
    }

    /**
     * Module WebUI serves the watch's local module files through the `ksu` bridge, which only the
     * watch's own WebView provides; a browser or the phone cannot reach them, so no external mode
     * is attempted.
     */
    fun openWebUi(moduleId: String, moduleName: String, mode: String) {
        viewModelScope.launch {
            mutableEvents.emit(when {
                mode == "browser" || mode == "phone" -> WearLinkEvent.Failed(null, webUi = true)
                repository.hasWebView() -> WearLinkEvent.WebUi(moduleId, moduleName)
                else -> WearLinkEvent.Failed(WearLinkFailure.WEBVIEW_UNAVAILABLE, webUi = true)
            })
        }
    }
    fun cancel() { task?.cancel() }
}
