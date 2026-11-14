package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.Text

/** Interactive labels wrap up to [maxLines], then scroll in one line when motion is allowed. */
@Composable
fun WearAutoMarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    textDecoration: TextDecoration? = null,
) {
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    var width by remember { mutableIntStateOf(0) }
    var overflowing by remember(text, maxLines, density, style, measurer) { mutableStateOf(false) }

    // Measure the wrapping layout independently: a marquee measures its child at infinite width.
    fun overflows(availableWidth: Int) = measurer.measure(
        AnnotatedString(text),
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = availableWidth),
    ).hasVisualOverflow
    val scrolling = overflowing && !reduceMotion
    Text(
        text = text,
        modifier = modifier.onSizeChanged {
            width = it.width
            overflowing = overflows(it.width)
        }.then(
            if (scrolling) {
                Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val edge = minOf(4.dp.toPx() / size.width.coerceAtLeast(1f), 0.5f)
                        // This alpha mask fades only the scrolling text, leaving its container intact.
                        drawRect(
                            Brush.horizontalGradient(0f to Color.Transparent, edge to Color.Black, 1f - edge to Color.Black, 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn,
                        )
                    }.basicMarquee(iterations = Int.MAX_VALUE)
            } else {
                Modifier
            },
        ),
        maxLines = if (scrolling) 1 else maxLines,
        softWrap = !scrolling,
        overflow = TextOverflow.Ellipsis,
        color = color,
        style = style,
        textDecoration = textDecoration,
        onTextLayout = {
            // Recheck changed text and fonts even when the row keeps the same bounded width.
            overflowing = overflows(if (width > 0) width else it.size.width)
        },
    )
}
