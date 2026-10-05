package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.SwipeToDismissBox
import coil.compose.AsyncImage
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.koin.compose.koinInject

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * The watchOS black page base. The app's single backdrop, including the user's cropped image and
 * chosen dimming; the components drawn over it are made translucent by the theme, so the image
 * shows through them. Black remains the fallback with no image, or until the image has loaded.
 * Rendered by WearManagerTheme outside all page and swipe-dismiss transitions.
 */
@Composable
fun WearBackground(content: @Composable BoxScope.() -> Unit) {
    val config = koinInject<ThemeConfig>()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        config.customBackgroundUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
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
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissValue.Dismissed) {
            state.snapTo(SwipeToDismissValue.Default)
            onDismissed()
        }
    }
    SwipeToDismissBox(state = state, backgroundScrimColor = Color.Transparent, content = content)
}
