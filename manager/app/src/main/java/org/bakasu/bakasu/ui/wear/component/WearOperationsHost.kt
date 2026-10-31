package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.wear.compose.foundation.LocalScreenIsActive

/** Keeps a menu in its owning navigation entry, including while that entry animates away. */
@Composable
internal fun WearOperationsHost(
    menuKey: Any?,
    menuOpen: Boolean,
    menuActive: Boolean,
    onBack: () -> Unit,
    menuContent: @Composable (onClosing: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    val pull = remember { mutableFloatStateOf(if (menuOpen) 1f else 0f) }
    var dismissals by remember { mutableIntStateOf(0) }
    var wasOpen by remember { mutableStateOf(menuOpen) }
    LaunchedEffect(menuOpen) {
        if (wasOpen && !menuOpen) {
            pull.floatValue = 0f
            dismissals++
        }
        wasOpen = menuOpen
    }
    val stateHolder = rememberSaveableStateHolder()
    var height by remember { mutableFloatStateOf(1f) }
    val screenActive = LocalScreenIsActive.current
    CompositionLocalProvider(
        LocalWearPanelDismissals provides dismissals,
        LocalWearPanelHosted provides menuOpen,
        LocalWearPanelPull provides pull,
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
            if (menuKey != null && (menuOpen || pull.floatValue > 0f)) {
                val dim = WearPanelMaxDim * (pull.floatValue / WearPanelDimReach).coerceIn(0f, 1f)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)).clearAndSetSemantics {})
                Box(
                    Modifier.fillMaxSize().onSizeChanged { height = it.height.toFloat() }
                        .graphicsLayer { translationY = (pull.floatValue - 1f) * height }
                        .clipToBounds().then(if (menuActive) Modifier else Modifier.clearAndSetSemantics {}),
                ) {
                    CompositionLocalProvider(LocalScreenIsActive provides (menuActive && screenActive)) {
                        WearSwipeToDismissBox(onDismissed = { if (menuActive) onBack() }) { isBackground ->
                            if (!isBackground) {
                                stateHolder.SaveableStateProvider(menuKey) {
                                    menuContent { dismissals++ }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
