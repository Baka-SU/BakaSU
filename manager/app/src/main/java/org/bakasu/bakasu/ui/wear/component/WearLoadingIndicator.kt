package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R

/** Blocks content interaction while a foreground link task is being prepared. */
@Composable
fun WearLoadingOverlay() {
    val label = stringResource(R.string.wear_loading)
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        WearLoadingIndicator(contained = true)
    }
}

/** The shared expressive loading animation, sized and colored for Wear surfaces. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WearLoadingIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, contained: Boolean = false) {
    if (contained) {
        // As with the phone's pull refresh, an opaque circular surface separates the animation
        // from content that has already loaded underneath it.
        Box(
            modifier.size(48.dp).shadow(6.dp, CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f), CircleShape).padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator(color = color)
        }
    } else {
        LoadingIndicator(modifier = modifier.size(48.dp), color = color)
    }
}
