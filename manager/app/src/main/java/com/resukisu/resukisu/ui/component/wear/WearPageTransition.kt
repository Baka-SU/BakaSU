package com.resukisu.resukisu.ui.component.wear

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.SwipeToDismissBoxState
import androidx.wear.compose.foundation.hierarchicalFocusGroup
import androidx.wear.compose.material3.MaterialTheme

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */
private data class WearPageState(val route: String, val depth: Int)
internal val LocalWearPageAnimation = staticCompositionLocalOf { "fade-scale" }

/**
 * The swipe-to-dismiss state of the page currently shown by the nearest [WearPageTransition].
 * [WearSwipeToDismissBox] uses it so the transition can show the parent page while the page is
 * swiped and leave the dismissed page off screen instead of snapping it back.
 */
internal class WearPageSwipe(val state: SwipeToDismissBoxState, val onDismissed: () -> Unit)
internal val LocalWearPageSwipe = compositionLocalOf<WearPageSwipe?> { null }

/**
 * Full-screen pages keep fixed layout constraints; transitions affect only their graphics layers.
 *
 * While the current page is swiped to dismiss, [parentRoute] (by default the route last shown one
 * level up) is drawn under it, and the return it triggers takes no further transition.
 */
@Composable
fun WearPageTransition(route: String, depth: Int, verticalRoutes: Set<String> = emptySet(),
    parentRoute: String? = null, content: @Composable (String) -> Unit) {
    val motion = MaterialTheme.motionScheme
    val reduceMotion = LocalReduceMotion.current
    val animation = LocalWearPageAnimation.current
    val target = WearPageState(route, depth)
    val transition = updateTransition(target, label = "Wear page transition")
    val pages = remember { mutableStateListOf(target) }
    // The page left by a swipe; it stays hidden until the return to its parent has finished.
    var swipeDismissed by remember { mutableStateOf<String?>(null) }
    if (transition.currentState == transition.targetState && !transition.isRunning) {
        pages.removeAll { it.route != route }
        if (swipeDismissed != null) swipeDismissed = null
    }
    val targetIndex = pages.indexOfFirst { it.route == route }
    if (targetIndex < 0) pages.add(target) else if (pages[targetIndex] != target) pages[targetIndex] = target
    val routeAtDepth = remember { mutableMapOf<Int, String>() }
    routeAtDepth[depth] = route
    val parent = parentRoute ?: routeAtDepth[depth - 1]
    val swipeState = remember(route) { SwipeToDismissBoxState() }
    val swipe = remember(swipeState) { WearPageSwipe(swipeState) { swipeDismissed = route } }
    val swiping by remember(swipeState) {
        derivedStateOf { swipeState.offset.let { !it.isNaN() && it > 0f } }
    }
    val shownPages = if (depth > 0 && swiping && parent != null && pages.none { it.route == parent })
        pages + WearPageState(parent, depth - 1) else pages
    // Keep a menu's slide direction while an interrupted opening returns to its parent.
    val vertical = pages.any { it.route in verticalRoutes }
    val sliding = vertical || animation == "slide"
    val skipAnimation = reduceMotion || swipeDismissed != null
    Box(Modifier.fillMaxSize().clipToBounds(), propagateMinConstraints = true) {
        shownPages.forEach { page -> key(page.route) {
            val offset = transition.animateFloat(
                transitionSpec = { if (skipAnimation) snap() else motion.defaultSpatialSpec() },
                label = "page-offset",
            ) { state ->
                if (!sliding || state.route == page.route) 0f
                else {
                    val towardsDeeper = state.depth > page.depth ||
                        (state.depth == page.depth && state == transition.targetState)
                    if (vertical) { if (towardsDeeper) 1f else -1f }
                    else { if (towardsDeeper) -1f else 1f }
                }
            }
            val opacity = transition.animateFloat(
                transitionSpec = { if (skipAnimation || sliding) snap() else motion.fastEffectsSpec() },
                label = "page-opacity",
            ) { state -> if (sliding || state.route == page.route) 1f else 0f }
            val scale = transition.animateFloat(
                transitionSpec = { if (skipAnimation || sliding) snap() else motion.defaultSpatialSpec() },
                label = "page-scale",
            ) { state ->
                if (sliding || state.route == page.route) 1f
                else if (state.depth > page.depth ||
                    (state.depth == page.depth && state == transition.targetState)) 1.05f else 0.85f
            }
            val current = page.route == route
            val hidden = page.route == swipeDismissed && !current
            // The parent stays in place under the swiped page and through the return it triggers.
            val revealed = page.route == parent && !current && swiping ||
                current && swipeDismissed != null
            val active = LocalScreenIsActive.current && current
            CompositionLocalProvider(LocalScreenIsActive provides active,
                LocalWearPageSwipe provides if (current && depth > 0) swipe else null) {
                Box(
                    Modifier.fillMaxSize().zIndex(page.depth.toFloat())
                        .graphicsLayer {
                            translationX = if (revealed || !sliding || vertical) 0f else offset.value * size.width
                            translationY = if (vertical && !revealed) offset.value * size.height else 0f
                            alpha = if (hidden) 0f else if (revealed) 1f else opacity.value.coerceIn(0f, 1f)
                            scaleX = if (revealed) 1f else scale.value
                            scaleY = if (revealed) 1f else scale.value
                        }.hierarchicalFocusGroup(active),
                    propagateMinConstraints = true,
                ) {
                    content(page.route)
                }
            }
        } }
    }
}
