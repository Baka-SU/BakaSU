package org.bakasu.bakasu.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import org.bakasu.bakasu.ui.util.LocalPagerPage

/**
 * Holds the top bar currently published by a pager page, how tall the host drew it, and the
 * size its title pill is currently drawn at.
 */
@Stable
class TopBarSlot {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
        internal set

    /** Measured height of the bar, including the status bar it covers. */
    var height by mutableStateOf(0.dp)
        internal set

    /**
     * Pager page that published [content].
     *
     * The bar outlives the selected page by a frame or two at a handover, so this - not the
     * selection - is what a swipe is measured from.
     */
    var page by mutableStateOf<Int?>(null)
        internal set

    /** The title pill's animated size, kept here so it outlives each bar it is drawn in. */
    val titlePillSize = TopBarPillSize()
}

val LocalTopBarSlot = compositionLocalOf<TopBarSlot?> { null }

/**
 * Publishes [content] as the shared top bar while [active].
 *
 * Pager pages cannot draw their own bar: each page translates as you swipe and carries its own
 * blur backdrop, so two scrims meet mid-drag and leave a visible seam. Publishing the bar instead
 * lets one host draw it outside the pager, while every piece of state it reads stays in the page.
 *
 * Only the active page publishes, so the pages composed either side of it do not compete.
 */
@Composable
fun ProvideTopBar(active: Boolean, content: @Composable () -> Unit) {
    val slot = LocalTopBarSlot.current ?: return
    val page = LocalPagerPage.current
    val latest by rememberUpdatedState(content)
    // stable identity, so publishing does not re-trigger on every recomposition of the page
    val published = remember { @Composable { latest() } }

    DisposableEffect(slot, active, published, page) {
        if (active) {
            slot.content = published
            slot.page = page
        }
        onDispose {
            if (slot.content === published) {
                slot.content = null
                slot.page = null
            }
        }
    }
}
