package org.bakasu.bakasu.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bakasu.bakasu.ui.component.liquid.lens
import org.bakasu.bakasu.ui.component.liquid.vibrancy
import org.bakasu.bakasu.ui.theme.CardConfig
import org.bakasu.bakasu.ui.theme.ScreenEdgePadding
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.theme.blurEffect
import org.bakasu.bakasu.ui.theme.isInDarkTheme
import org.bakasu.bakasu.ui.util.LocalBlurState
import org.bakasu.bakasu.ui.util.LocalTopBarSwipeDelta
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop

/** Breathing room inside a pill, and the gap each one keeps from its neighbour. */
private val PillHorizontalPadding = 6.dp

/** Material3's own horizontal padding on the top app bar's navigation and action rows. */
private val TopAppBarIconRowPadding = 4.dp

/** Padding around a title's text inside its pill. Interior spacing, not the screen gutter. */
private val PillTextPadding = 16.dp

/** Strong enough to smear scrolling content into an unreadable wash behind a pill. */
private val PillBlurRadius = 16.dp

/**
 * Inset that puts a top app bar's leading and trailing icon pills on the [ScreenEdgePadding]
 * gutter, making up whatever [TopAppBarIconRowPadding] and [PillHorizontalPadding] leave over.
 */
val TopBarIconEdgeInset = ScreenEdgePadding - TopAppBarIconRowPadding - PillHorizontalPadding

/**
 * Capsule container for top app bar content: a refracted, frosted pill while the blur backdrop
 * is available, a solid one otherwise.
 *
 * Unlike [FloatingBottomBar], which floats over empty space, a pill sits directly on scrolling
 * content — so it tints and blurs hard enough to stop that content reading through it.
 */
@Composable
private fun TopBarPill(
    modifier: Modifier = Modifier,
    shadowRadius: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val isInDark = isInDarkTheme(themeConfig.forceDarkMode)
    val backdrop = LocalBlurState.current
    // Same surface the cards use, so the pills follow the Card transparency setting.
    // cardAlpha is 1f without a custom background, which would hide the blur entirely, so
    // fall back to blurEffect()'s haze-like 0.8f whenever there is a backdrop to show.
    val tintAlpha = when {
        cardConfig.isCustomBackgroundEnabled -> cardConfig.cardAlpha
        backdrop != null -> 0.8f
        else -> 1f
    }
    val containerColor = MaterialTheme.colorScheme.surfaceBright.copy(alpha = tintAlpha)

    Box(
        modifier = modifier
            .dropShadow(
                shape = CircleShape,
                shadow = Shadow(
                    radius = shadowRadius,
                    color = Color.Black,
                    alpha = if (isInDark) 0.2f else 0.1f,
                ),
            )
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(PillBlurRadius.toPx(), PillBlurRadius.toPx())
                            lens(
                                refractionHeight = 12.dp.toPx(),
                                refractionAmount = 12.dp.toPx(),
                            )
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                } else {
                    Modifier.background(containerColor, CircleShape)
                }
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * The title pill's size, animated and owned outside the bar that draws it.
 *
 * Each pager page publishes its own bar, so a handover composes a new pill and whatever the old
 * one remembered goes with it - the pill would simply appear at the next title's width. Holding
 * the animation out here lets that new pill start from the width the old one left behind and grow
 * or shrink into its own.
 */
@Stable
class TopBarPillSize {
    private var animated: Animatable<IntSize, AnimationVector2D>? = null

    /**
     * The size to lay the pill out at, for content that measured to [target].
     *
     * The first title has nothing to grow out of and sets the starting size; every title after it
     * is animated to.
     */
    internal fun sizeFor(
        target: IntSize,
        spec: FiniteAnimationSpec<IntSize>,
        scope: CoroutineScope
    ): IntSize {
        val animation = animated
            ?: Animatable(target, IntSize.VectorConverter).also { animated = it }

        // The second test restarts an animation that was cut short: the scope belongs to the pill
        // that launched it, so a handover mid-grow cancels it and would strand the pill part of
        // the way there.
        if (animation.targetValue != target ||
            (!animation.isRunning && animation.value != target)
        ) {
            scope.launch { animation.animateTo(target, spec) }
        }
        return animation.value
    }
}

/**
 * Lays content out at [pillSize]'s animated size rather than the size it measured to, keeping it
 * centred while the two differ.
 *
 * Without a [pillSize] - anywhere outside the pager's shared bar, where nothing swaps the pill
 * out mid-flight - the measured size is used as it stands.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Modifier.animatePillSize(pillSize: TopBarPillSize?): Modifier {
    if (pillSize == null) return this
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    val scope = rememberCoroutineScope()

    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val target = IntSize(placeable.width, placeable.height)
        val size = constraints.constrain(pillSize.sizeFor(target, spec, scope))
        val direction = layoutDirection
        layout(size.width, size.height) {
            placeable.place(Alignment.Center.align(target, size, direction))
        }
    }
}

/**
 * Wraps a top app bar title in a [TopBarPill].
 *
 * The pill itself stays put while swiping between pages - only the words inside travel, clipped
 * to the pill - and the pill resizes between one title and the next rather than cutting.
 */
@Composable
fun TopBarTitlePill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val swipeDelta = LocalTopBarSwipeDelta.current
    val shiftPx = with(LocalDensity.current) { TopBarSwipeShift.toPx() }

    TopBarPill(modifier) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .animatePillSize(LocalTopBarSlot.current?.titlePillSize)
                .padding(horizontal = PillTextPadding, vertical = 6.dp)
                .graphicsLayer {
                    val delta = swipeDelta()
                    translationX = -delta * shiftPx
                    alpha = swipeAlpha(delta)
                }
        ) {
            content()
        }
    }
}

/** A top app bar navigation or action button sitting inside a circular [TopBarPill]. */
@Composable
fun TopBarIconPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val swipeDelta = LocalTopBarSwipeDelta.current
    val shiftPx = with(LocalDensity.current) { TopBarSwipeShift.toPx() }

    TopBarPill(
        modifier = modifier
            .padding(horizontal = PillHorizontalPadding)
            .graphicsLayer {
                val delta = swipeDelta()
                translationX = -delta * shiftPx
                alpha = swipeAlpha(delta)
            },
        shadowRadius = 4.dp
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp),
            enabled = enabled,
            content = content
        )
    }
}

/** Top app bar colors for the pill design: the bar itself paints nothing. */
@Composable
fun transparentTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
)

/**
 * Window insets for the pill design, topping [base] up so the trailing action pill lands on the
 * [ScreenEdgePadding] gutter. The leading pill gets its own [TopBarIconEdgeInset] instead, so that
 * the title still starts on the gutter on screens with no navigation icon.
 *
 * [WindowInsets] sides are physical rather than directional, so the inset follows the layout
 * direction by hand — the actions row moves to the physical left under RTL.
 */
@Composable
fun pillTopAppBarWindowInsets(
    base: WindowInsets = TopAppBarDefaults.windowInsets
): WindowInsets = base.add(
    if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
        WindowInsets(right = TopBarIconEdgeInset)
    } else {
        WindowInsets(left = TopBarIconEdgeInset)
    }
)

/** Steps used to approximate the eased fade; enough that the ramp reads as smooth. */
private const val TopBarScrimSteps = 12

/** How far below the bar the scrim keeps fading, so the ramp has room to end invisibly. */
private val TopBarScrimOverflow = 72.dp

/**
 * Height of the title row the scrim covers, below the status bar.
 *
 * Fixed rather than taken from the bar: the search pages' bars are taller, so a scrim measured
 * from its content jumps for a frame at the hand-over and reads as a flash. It also keeps the
 * scrim identical on every page.
 */
private val TopBarScrimRow = 64.dp

/**
 * Tint laid over the blur inside the scrim.
 *
 * blurEffect's own tint follows the card transparency setting, so on a custom background it can
 * be far too weak to stop text reading through. This is applied on top of the blur and inside the
 * same mask, so it fades out with it.
 */
private const val TopBarScrimTint = 0.55f

/**
 * Smoothstep alphas for the scrim ramp, computed once for the process.
 *
 * Smoothstep is flat at the top, so the scrim holds its full value up at the status bar and then
 * eases away with no edge where the falloff begins. Raising it to a power below one lifts the
 * middle of the curve, keeping the blur strong for more of its run.
 */
private val TopBarScrimAlphas = FloatArray(TopBarScrimSteps + 1) { step ->
    val t = step / TopBarScrimSteps.toFloat()
    (1f - t * t * (3f - 2f * t)).pow(0.7f)
}

/** How far bar content travels while swiping between pages. */
private val TopBarSwipeShift = 40.dp

/**
 * Opacity for bar content at a given swipe [delta].
 *
 * Linear would leave the pill visibly empty either side of the handover, where only one page
 * publishes a bar and there is nothing to cross-fade with. Squaring the ramp keeps content
 * legible for most of the gesture and collapses the blank moment to the handover itself.
 */
private fun swipeAlpha(delta: Float): Float {
    val t = (1f - abs(delta) * 2f).coerceIn(0f, 1f)
    return t * (2f - t)
}

/**
 * Draws the status bar scrim behind a top app bar.
 *
 * Top app bars paint nothing themselves, so content scrolls up behind the system clock and
 * icons. This puts a blurred scrim back — solid across the status bar, then fading to nothing
 * by the bottom of the bar, so it dissolves into the content instead of ending on a hard line.
 *
 * The scrim is a sibling drawn before [content] rather than a modifier on the bar itself: the
 * gradient erases pixels with [BlendMode.DstIn], which would eat the pills too if they shared
 * its layer.
 */
@Composable
fun TopBarScrim(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val statusBarPx = with(density) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
    }
    val scrimPx = with(density) {
        (statusBarPx + (TopBarScrimRow + TopBarScrimOverflow).toPx()).roundToInt()
    }
    val tint = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = TopBarScrimTint)

    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                // draw a fixed height while still reporting the bar's, so the tail has room to
                // reach zero and the scrim never resizes when the bar behind it changes
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = scrimPx, maxHeight = scrimPx)
                    )
                    layout(placeable.width, constraints.maxHeight) { placeable.place(0, 0) }
                }
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                // drawWithCache, not drawWithContent: the ramp only depends on the bar height,
                // so the stops and the brush are built once per size instead of every frame.
                .drawWithCache {
                    // Hold full strength across the status bar, then ease away over the rest of
                    // the scrim. Smoothstep leaves the plateau with zero slope, so the join is
                    // smooth and there is no line where the falloff starts.
                    val solidStop = (statusBarPx / size.height).coerceIn(0f, 0.9f)
                    val stops = Array(TopBarScrimAlphas.size + 2) { index ->
                        when (index) {
                            0 -> 0f to Color.Black
                            TopBarScrimAlphas.size + 1 -> 1f to Color.Transparent
                            else -> {
                                val step = index - 1
                                val t = step / TopBarScrimSteps.toFloat()
                                solidStop + (1f - solidStop) * t to
                                        Color.Black.copy(alpha = TopBarScrimAlphas[step])
                            }
                        }
                    }
                    val brush = Brush.verticalGradient(colorStops = stops)
                    onDrawWithContent {
                        drawContent()
                        drawRect(color = tint)
                        drawRect(brush = brush, blendMode = BlendMode.DstIn)
                    }
                }
                .blurEffect()
        )
        content()
    }
}
