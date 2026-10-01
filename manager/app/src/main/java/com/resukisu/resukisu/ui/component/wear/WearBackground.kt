package com.resukisu.resukisu.ui.component.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.wear.compose.material3.SwipeToDismissBox
import coil.compose.AsyncImage
import com.resukisu.resukisu.ui.theme.ThemeConfig
import org.koin.compose.koinInject

/**
 * The app backdrop: the black base, the custom background image and its dim layer (a black layer at
 * the backgroundDim alpha, as on the phone), then [content].
 */
@Composable
fun WearBackground(content: @Composable BoxScope.() -> Unit) {
    val config = koinInject<ThemeConfig>()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        config.customBackgroundUri?.let { uri ->
            AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = config.backgroundDim)))
        }
        content()
    }
}

/**
 * [SwipeToDismissBox] with the app backdrop. The Material box paints its foreground with the
 * theme's black background, which would hide a backdrop drawn behind it, so both the foreground and
 * the layer revealed while swiping draw the backdrop themselves.
 */
@Composable
fun WearSwipeToDismissBox(onDismissed: () -> Unit, content: @Composable BoxScope.(isBackground: Boolean) -> Unit) {
    SwipeToDismissBox(onDismissed = onDismissed) { isBackground ->
        WearBackground { content(isBackground) }
    }
}
