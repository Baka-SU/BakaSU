package org.bakasu.bakasu.ui.wear.component

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwipeToDismissBox
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import org.bakasu.bakasu.ui.theme.BackgroundManager
import org.bakasu.bakasu.ui.theme.LocalBackgroundRenderState
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.theme.extractSeedColor
import org.koin.compose.koinInject

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * The watchOS black page base. The app's single backdrop, including the user's cropped image and
 * chosen dimming; the components drawn over it are made translucent by the theme, so the image
 * shows through them. Black remains the fallback with no image, or until the image has loaded.
 * The plain black base follows predictive back; a selected image and its dim layer remain intact.
 * Rendered by WearManagerTheme outside page transitions.
 */
@Composable
fun WearBackground(content: @Composable BoxScope.() -> Unit) {
    val config = koinInject<ThemeConfig>()
    val backgroundManager = koinInject<BackgroundManager>()
    val renderState = LocalBackgroundRenderState.current
    val scope = rememberCoroutineScope()
    val transition = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
        ?.transitionState?.collectAsStateWithLifecycle()?.value
    val back = (transition as? NavigationEventTransitionState.InProgress)
        ?.takeIf { it.direction == NavigationEventTransitionState.TRANSITIONING_BACK }
    val baseColor = MaterialTheme.colorScheme.background
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (config.customBackgroundUri != null) 1f else (1f - (back?.latestEvent?.progress ?: 0f)).coerceIn(0f, 1f),
        animationSpec = if (back != null) snap() else MaterialTheme.motionScheme.defaultEffectsSpec<Float>(),
        label = "wear-back-background",
    )
    Box(Modifier.fillMaxSize().drawBehind { drawRect(baseColor.copy(alpha = backgroundAlpha)) }) {
        config.customBackgroundUri?.let { uri ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(uri).allowHardware(false).build(),
                onSuccess = { result ->
                    scope.launch {
                        val seed = result.result.drawable.toBitmap().extractSeedColor()
                        if (config.customBackgroundUri == uri) {
                            backgroundManager.saveBackgroundSeedColor(seed)
                            renderState.seedColor = seed
                        }
                    }
                },
                onError = { config.customBackgroundUri = null },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (config.isEnableBlur && Build.VERSION.SDK_INT >= 31) Modifier.blur(25.dp) else Modifier),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = config.backgroundDim.coerceIn(0f, 1f))))
        }
        content()
    }
}

/**
 * Swipe-dismiss layers contain only page content; the theme owns the stationary backdrop. The
 * default background scrim is an opaque fill under the foreground page that would hide that
 * backdrop, so it is transparent; the content scrim still dims the page while it is swiped away.
 */
@Composable
fun WearSwipeToDismissBox(onDismissed: () -> Unit, content: @Composable BoxScope.(isBackground: Boolean) -> Unit) {
    // Wear API 36+ delivers predictive back through the platform, just like the Navigation 3
    // Wear scene strategy. A second touch-driven dismiss box competes with back-to-home.
    if (Build.VERSION.SDK_INT >= 36 || !LocalWearSwipeEnabled.current) {
        Box(Modifier.fillMaxSize()) { content(false) }
    } else {
        SwipeToDismissBox(onDismissed = onDismissed, backgroundScrimColor = Color.Transparent, content = content)
    }
}
