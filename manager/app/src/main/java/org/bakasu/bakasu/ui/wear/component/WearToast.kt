package org.bakasu.bakasu.ui.wear.component

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.LocalReduceMotion
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

/** Short non-modal feedback, with an optional curved presentation on round screens. */
@Composable
fun WearToastHost(style: String = "standard", content: @Composable () -> Unit) {
    val state = remember { WearToastState() }
    val accessibility = LocalAccessibilityManager.current
    val reduceMotion = LocalReduceMotion.current
    val round = style == "round" && LocalConfiguration.current.isScreenRound
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
                enter = fadeIn(tween(220)) + if (round && !reduceMotion) slideInVertically(tween(400, easing = CubicBezierEasing(0.2f, 1.4f, 0.4f, 1f))) { 100 } else slideInVertically(tween(0)) { 0 },
                exit = fadeOut(tween(220)) + if (round && !reduceMotion) slideOutVertically(tween(400)) { 100 } else slideOutVertically(tween(0)) { 0 },
            ) {
                displayedMessage?.let { message ->
                    val density = LocalDensity.current
                    val colors = MaterialTheme.colorScheme
                    val textStyle = MaterialTheme.typography.bodySmall
                    val paint = remember(density, textStyle, colors.onSurface) {
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            textSize = with(density) { textStyle.fontSize.toPx() }
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            color = colors.onSurface.toArgb()
                        }
                    }
                    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                    val radius = with(density) { (screenWidth / 2 - 20.dp).toPx() }
                    val fitsArc = !message.contains('\n') && paint.fontSpacing <= with(density) { 24.dp.toPx() } && paint.measureText(message) <= radius * Math.toRadians(140.0)
                    if (round && fitsArc) {
                        WearRoundToast(message, paint)
                    } else {
                        Box(
                            Modifier.fillMaxWidth().background(
                                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))),
                            ).padding(top = 24.dp),
                        ) {
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
                                maxLines = Int.MAX_VALUE,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The arc stays inside the host window and needs no overlay permission or additional window. */
@Composable
private fun WearRoundToast(message: String, paint: Paint) {
    val colors = MaterialTheme.colorScheme
    Canvas(
        Modifier.fillMaxSize().semantics {
            contentDescription = message
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        val thickness = 24.dp.toPx()
        val radius = minOf(size.width, size.height) / 2 - 8.dp.toPx() - thickness / 2
        val sweep = Math.toDegrees((paint.measureText(message) / radius).toDouble()).toFloat().plus(12f).coerceIn(20f, 140f)
        val cx = size.width / 2
        val cy = size.height / 2
        drawIntoCanvas { canvas ->
            val band = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = thickness
                strokeCap = Paint.Cap.ROUND
                color = colors.surfaceContainer.copy(alpha = 0.9f).toArgb()
            }
            canvas.nativeCanvas.drawArc(band, 90f - sweep / 2, sweep, false, background)
            val outline = Paint(background).apply {
                strokeWidth = 0.8.dp.toPx()
                color = colors.outlineVariant.toArgb()
            }
            val outer = RectF(band).apply { inset(-thickness / 2, -thickness / 2) }
            canvas.nativeCanvas.drawArc(outer, 90f - sweep / 2, sweep, false, outline)
            val baseline = radius + paint.textSize / 3
            val path = Path().apply { addArc(cx - baseline, cy - baseline, cx + baseline, cy + baseline, 90f + sweep / 2, -sweep) }
            val length = baseline * Math.toRadians(sweep.toDouble()).toFloat()
            canvas.nativeCanvas.drawTextOnPath(message, path, (length - paint.measureText(message)) / 2, 0f, paint)
        }
    }
}
