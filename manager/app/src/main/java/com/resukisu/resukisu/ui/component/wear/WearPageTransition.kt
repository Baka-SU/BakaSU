package com.resukisu.resukisu.ui.component.wear

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.hierarchicalFocusGroup
import androidx.wear.compose.material3.MaterialTheme

private data class WearPageState(val route: String, val depth: Int)
internal val LocalWearPageAnimation = staticCompositionLocalOf { "fade-scale" }

/** Connects parent and child surfaces with the selected transition and the theme's motion specs. */
@Composable
fun WearPageTransition(route: String, depth: Int, verticalRoutes: Set<String> = emptySet(),
    content: @Composable (String) -> Unit) {
    val motion = MaterialTheme.motionScheme
    val reduceMotion = LocalReduceMotion.current
    val animation = LocalWearPageAnimation.current
    AnimatedContent(
        targetState = WearPageState(route, depth),
        modifier = Modifier.fillMaxSize(),
        contentKey = { it.route },
        transitionSpec = {
            if (reduceMotion) return@AnimatedContent (EnterTransition.None togetherWith ExitTransition.None).using(null)
            val forward = targetState.depth >= initialState.depth
            val vertical = targetState.route in verticalRoutes || initialState.route in verticalRoutes
            val enter = if (vertical)
                slideInVertically(motion.defaultSpatialSpec()) { height -> if (forward) height else -height }
            else if (animation == "slide")
                slideInHorizontally(motion.defaultSpatialSpec()) { width -> if (forward) width else -width }
            else scaleIn(motion.defaultSpatialSpec(), initialScale = if (forward) 0.85f else 1.05f) +
                fadeIn(motion.fastEffectsSpec())
            val exit = if (vertical)
                slideOutVertically(motion.defaultSpatialSpec()) { height -> if (forward) -height else height }
            else if (animation == "slide")
                slideOutHorizontally(motion.defaultSpatialSpec()) { width -> if (forward) -width else width }
            else scaleOut(motion.defaultSpatialSpec(), targetScale = if (forward) 1.05f else 0.85f) +
                fadeOut(motion.fastEffectsSpec())
            (enter togetherWith exit).using(null)
        },
        label = "Wear page transition",
    ) { page ->
        val active = LocalScreenIsActive.current && page.route == route
        CompositionLocalProvider(LocalScreenIsActive provides active) {
            Box(Modifier.fillMaxSize().hierarchicalFocusGroup(active)) {
                content(page.route)
            }
        }
    }
}
