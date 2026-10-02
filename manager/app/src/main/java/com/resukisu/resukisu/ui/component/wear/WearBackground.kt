package com.resukisu.resukisu.ui.component.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.SwipeToDismissBox

/**
 * The watch's black page base. Custom images are drawn inside neutral content cards.
 */
@Composable
fun WearBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        content()
    }
}

/**
 * Both the foreground and the layer revealed during swipe dismiss keep the same black page base.
 */
@Composable
fun WearSwipeToDismissBox(onDismissed: () -> Unit, content: @Composable BoxScope.(isBackground: Boolean) -> Unit) {
    SwipeToDismissBox(onDismissed = onDismissed) { isBackground ->
        WearBackground { content(isBackground) }
    }
}
