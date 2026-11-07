package org.bakasu.bakasu.ui.wear.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

private val externalWearToastMessages = Channel<String>(Channel.BUFFERED)

internal fun showWearToast(message: String) {
    externalWearToastMessages.trySend(message)
}

class WearToastState {
    var message by mutableStateOf<String?>(null)
        private set
    var sequence by mutableIntStateOf(0)
        private set

    fun show(message: String) {
        this.message = message
        sequence++
    }

    internal fun dismiss() {
        message = null
    }
}

val LocalWearToast = staticCompositionLocalOf<WearToastState> { error("WearToastHost is required") }

/** Short, non-modal feedback uses the same horizontal layout on round and square screens. */
@Composable
fun WearToastHost(content: @Composable () -> Unit) {
    val state = remember { WearToastState() }
    val accessibility = LocalAccessibilityManager.current
    var displayedMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let { displayedMessage = it }
    }
    LaunchedEffect(state) {
        for (message in externalWearToastMessages) state.show(message)
    }
    LaunchedEffect(state.sequence) {
        if (state.message != null) {
            delay(accessibility?.calculateRecommendedTimeoutMillis(2500L, containsText = true) ?: 2500L)
            state.dismiss()
        }
    }
    CompositionLocalProvider(LocalWearToast provides state) {
        Box(Modifier.fillMaxSize()) {
            content()
            AnimatedVisibility(
                visible = state.message != null,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(220)),
            ) {
                displayedMessage?.let { message ->
                    Box(Modifier.fillMaxWidth().height(72.dp)) {
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.12f),
                                        Color.Black.copy(alpha = 0.36f),
                                        Color.Black.copy(alpha = 0.62f),
                                    ),
                                ),
                            ),
                        )
                        Text(
                            text = message,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 12.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            softWrap = true,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
