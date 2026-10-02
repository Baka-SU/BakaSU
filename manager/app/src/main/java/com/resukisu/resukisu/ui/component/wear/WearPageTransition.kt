package com.resukisu.resukisu.ui.component.wear

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.hierarchicalFocusGroup
import androidx.wear.compose.material3.MaterialTheme

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */
private data class WearPageState(val route: String, val depth: Int)
internal val LocalWearPageAnimation = staticCompositionLocalOf { "fade-scale" }

/** Full-screen pages keep fixed layout constraints; transitions affect only their graphics layers. */
@Composable
fun WearPageTransition(route: String, depth: Int, verticalRoutes: Set<String> = emptySet(),
    content: @Composable (String) -> Unit) {
    val motion = MaterialTheme.motionScheme
    val reduceMotion = LocalReduceMotion.current
    val animation = LocalWearPageAnimation.current
    val target = WearPageState(route, depth)
    val transition = updateTransition(target, label = "Wear page transition")
    val pages = remember { mutableStateListOf(target) }
    if (transition.currentState == transition.targetState && !transition.isRunning) {
        pages.removeAll { it.route != route }
    }
    val targetIndex = pages.indexOfFirst { it.route == route }
    if (targetIndex < 0) pages.add(target) else if (pages[targetIndex] != target) pages[targetIndex] = target
    // Keep a menu's slide direction while an interrupted opening returns to its parent.
    val vertical = pages.any { it.route in verticalRoutes }
    val sliding = vertical || animation == "slide"
    Box(Modifier.fillMaxSize().clipToBounds(), propagateMinConstraints = true) {
        pages.forEach { page -> key(page.route) {
            val offset = transition.animateFloat(
                transitionSpec = { if (reduceMotion) snap() else motion.defaultSpatialSpec() },
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
                transitionSpec = { if (reduceMotion || sliding) snap() else motion.fastEffectsSpec() },
                label = "page-opacity",
            ) { state -> if (sliding || state.route == page.route) 1f else 0f }
            val scale = transition.animateFloat(
                transitionSpec = { if (reduceMotion || sliding) snap() else motion.defaultSpatialSpec() },
                label = "page-scale",
            ) { state ->
                if (sliding || state.route == page.route) 1f
                else if (state.depth > page.depth ||
                    (state.depth == page.depth && state == transition.targetState)) 1.05f else 0.85f
            }
            val active = LocalScreenIsActive.current && page.route == route
            CompositionLocalProvider(LocalScreenIsActive provides active) {
                Box(
                    Modifier.fillMaxSize().zIndex(page.depth.toFloat())
                        .graphicsLayer {
                            translationX = if (sliding && !vertical) offset.value * size.width else 0f
                            translationY = if (vertical) offset.value * size.height else 0f
                            alpha = opacity.value.coerceIn(0f, 1f)
                            scaleX = scale.value
                            scaleY = scale.value
                        }.hierarchicalFocusGroup(active),
                    propagateMinConstraints = true,
                ) {
                    content(page.route)
                }
            }
        } }
    }
}
