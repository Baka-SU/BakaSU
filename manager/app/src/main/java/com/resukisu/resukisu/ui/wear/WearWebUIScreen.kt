package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.rememberCustomDialog
import com.resukisu.resukisu.ui.component.wear.WearLoadingScreen
import com.resukisu.resukisu.ui.component.wear.WearSubPage
import com.resukisu.resukisu.ui.component.wear.WearSwipeToDismissBox
import com.resukisu.resukisu.ui.component.wear.WearTextInputPage
import com.resukisu.resukisu.ui.component.wear.WearTimeText
import com.resukisu.resukisu.ui.webui.WebUIEvent
import com.resukisu.resukisu.ui.webui.WebUIScreen
import com.resukisu.resukisu.ui.webui.WebUIState

/** Keeps the module's existing local WebView and root bridge inside a watch navigation surface. */
@Composable
internal fun WearWebUIScreen(state: WebUIState, onFinish: () -> Unit) {
    val back: () -> Unit = { if (state.webCanGoBack) state.webView?.goBack() else onFinish() }
    AppScaffold(timeText = { WearTimeText() }) {
        WearSwipeToDismissBox(onDismissed = back) { background ->
            if (!background) {
                when (val event = state.uiEvent) {
                    WebUIEvent.Loading -> WearLoadingScreen()
                    else -> Box(Modifier.fillMaxSize()) {
                        // Keep the WebView attached while a prompt is open, including its history,
                        // file result launcher and lifecycle observer.
                        WearWebContent(state, back)
                        if (!state.pageLoaded) WearLoadingScreen()
                        if (event is WebUIEvent.ShowPrompt) WearSubPage({ state.onPromptResult(null) }) {
                            WearTextInputPage(event.message, event.defaultValue) { state.onPromptResult(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WearWebContent(state: WebUIState, onBack: () -> Unit) {
    val webView = state.webView
    var offset by remember(webView) { mutableIntStateOf(0) }
    var scrollable by remember(webView) { mutableStateOf(false) }
    var atBottom by remember(webView) { mutableStateOf(false) }
    LaunchedEffect(webView, state.pageLoaded) {
        offset = webView?.scrollY ?: 0
        scrollable = webView?.let { it.canScrollVertically(-1) || it.canScrollVertically(1) } == true
        atBottom = webView?.canScrollVertically(1) != true
    }
    val edgeSpace = with(LocalDensity.current) { 64.dp.toPx() }
    val provider = remember(webView, edgeSpace) { object : ScrollInfoProvider {
        override val isScrollAwayValid get() = true
        override val isScrollable get() = scrollable
        override val isScrollInProgress get() = false
        override val anchorItemOffset get() = offset.toFloat()
        override val lastItemOffset get() = if (atBottom) edgeSpace else 0f
    } }
    DisposableEffect(webView) {
        webView?.isVerticalScrollBarEnabled = true
        webView?.setBackgroundColor(android.graphics.Color.BLACK)
        webView?.setOnScrollChangeListener { _, _, y, _, _ ->
            offset = y
            scrollable = webView.canScrollVertically(-1) || webView.canScrollVertically(1)
            atBottom = !webView.canScrollVertically(1)
        }
        onDispose { webView?.setOnScrollChangeListener(null) }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(webView) { if (webView != null) focus.requestFocus() }
    // An inset rectangle keeps module controls inside the circular display. Module HTML remains
    // unchanged; the asset loader, JS bridge, file chooser and history stay shared with the phone.
    val configuration = LocalConfiguration.current
    ScreenScaffold(scrollInfoProvider = provider, edgeButton = {
        EdgeButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.back))
        }
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val side = if (configuration.isScreenRound) maxWidth * 0.1465f else 8.dp
            Box(Modifier.fillMaxSize().padding(horizontal = side, vertical = 4.dp)) {
                WebUIScreen(state, isWear = true, modifier = Modifier.onRotaryScrollEvent {
                    webView?.scrollBy(0, it.verticalScrollPixels.toInt())
                    true
                }.focusRequester(focus).focusable())
            }
        }
    }
}

@Composable
internal fun HandleWearWebUIEvent(state: WebUIState) {
    val event = state.uiEvent
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
        AlertDialog(visible = true, onDismissRequest = { respond(false) },
            title = { Text(title) }, text = { Text(message) },
            confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = { respond(true) }) },
            dismissButton = { AlertDialogDefaults.DismissButton(onClick = { respond(false) }) })
    }
    LaunchedEffect(event) { dialog.show() }
}
