package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureScope
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
import kotlin.math.ceil

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
    val measurer = rememberTextMeasurer()
    val resolvedStyle = LocalTextStyle.current.merge(style)
    // Wear buttons query intrinsic sizes. Supply text metrics before the subcomposed layout,
    // whose own intrinsic measurements are unsupported, while keeping the same wrapping policy.
    val textIntrinsics = remember(measurer, text, resolvedStyle, maxLines, reduceMotion) {
        object : LayoutModifier {
            private fun measure(width: Int = Constraints.Infinity) = measurer.measure(
                AnnotatedString(text),
                style = resolvedStyle,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = width),
            )

            private fun height(width: Int): Int {
                val wrapped = measure(width)
                return if (wrapped.hasVisualOverflow && !reduceMotion) {
                    measurer.measure(AnnotatedString(text.replace('\n', ' ').replace('\r', ' ')), style = resolvedStyle, maxLines = 1).size.height
                } else {
                    wrapped.size.height
                }
            }

            override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints) = measurable.measure(constraints).let {
                layout(it.width, it.height) { it.placeRelative(0, 0) }
            }

            override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = ceil(measure().multiParagraph.minIntrinsicWidth).toInt()

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = ceil(measure().multiParagraph.maxIntrinsicWidth).toInt()

            override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = height(width)

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = height(width)
        }
    }
    // Decide from the parent's bounded width before laying out Text. Measuring the rendered
    // marquee instead feeds its unbounded width back into the decision and toggles line counts.
    BoxWithConstraints(modifier.then(textIntrinsics).clipToBounds(), propagateMinConstraints = true) {
        val overflowing = constraints.hasBoundedWidth && measurer.measure(
            AnnotatedString(text),
            style = resolvedStyle,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = constraints.maxWidth),
        ).hasVisualOverflow
        val scrolling = overflowing && !reduceMotion
        Text(
            text = if (scrolling) text.replace('\n', ' ').replace('\r', ' ') else text,
            modifier = if (scrolling) {
                Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val edge = minOf(4.dp.toPx() / size.width.coerceAtLeast(1f), 0.5f)
                        // Fade only the text; the card's surface stays opaque.
                        drawRect(
                            Brush.horizontalGradient(0f to Color.Transparent, edge to Color.Black, 1f - edge to Color.Black, 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn,
                        )
                    }.basicMarquee(iterations = Int.MAX_VALUE)
            } else {
                Modifier
            },
            maxLines = if (scrolling) 1 else maxLines,
            softWrap = !scrolling,
            overflow = TextOverflow.Ellipsis,
            color = color,
            style = resolvedStyle,
            textDecoration = textDecoration,
        )
    }
}
