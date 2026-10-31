package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.background
import androidx.wear.compose.foundation.curvedRow
import androidx.wear.compose.foundation.padding
import androidx.wear.compose.material3.CurvedTextDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.curvedText
import kotlinx.coroutines.delay

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

/** Short, non-modal feedback follows the bottom edge on round screens and stays horizontal on square screens. */
@Composable
fun WearToastHost(content: @Composable () -> Unit) {
    val state = remember { WearToastState() }
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(state.sequence) {
        if (state.message != null) {
            delay(accessibility?.calculateRecommendedTimeoutMillis(2500L, containsText = true) ?: 2500L)
            state.dismiss()
        }
    }
    CompositionLocalProvider(LocalWearToast provides state) {
        Box(Modifier.fillMaxSize()) {
            content()
            state.message?.let { message ->
                val colors = MaterialTheme.colorScheme
                val textStyle = MaterialTheme.typography.bodyMedium
                val container = colors.surfaceContainerHigh.copy(alpha = 0.95f)
                val modifier = Modifier.fillMaxSize().padding(8.dp).semantics { liveRegion = LiveRegionMode.Polite }
                if (LocalConfiguration.current.isScreenRound) {
                    CurvedLayout(modifier, anchor = 90f, angularDirection = CurvedDirection.Angular.Reversed) {
                        curvedRow(CurvedModifier.background(container, StrokeCap.Round).padding(radial = 8.dp, angular = 12.dp)) {
                            curvedText(
                                message,
                                style = CurvedTextStyle(textStyle.copy(color = colors.onSurface)),
                                maxSweepAngle = CurvedTextDefaults.StaticContentMaxSweepAngle,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    Box(modifier, contentAlignment = Alignment.BottomCenter) {
                        Text(
                            message,
                            modifier = Modifier.background(container, MaterialTheme.shapes.large).padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
