package org.bakasu.bakasu.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
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

    /** Measured height of the bar on screen, including the status bar it covers. */
    var height by mutableStateOf(0.dp)
        internal set

    private val settledHeights = mutableStateMapOf<Int, Dp>()

    /** Records [value] as [page]'s own bar height. Only called with the pager at rest. */
    internal fun recordHeight(page: Int?, value: Dp) {
        if (page != null && value > 0.dp) settledHeights[page] = value
    }

    /**
     * Height to lay [page]'s content out below.
     *
     * A page keeps to the bar it publishes itself rather than the one currently on screen. Mid
     * swipe those are not the same bar: the field folds away and then the next page's bar takes
     * over, and content that followed the live height would be dragged along by both - the
     * handover landing as a jump, since a flick hands over before the fold has finished.
     */
    fun heightFor(page: Int?): Dp = settledHeights[page] ?: height

    /**
     * Pager page that published [content].
     *
     * The bar outlives the selected page by a frame or two at a handover, so this - not the
     * selection - is what a swipe is measured from.
     */
    var page by mutableStateOf<Int?>(null)
        internal set

    private val fieldExpansion = mutableStateMapOf<Int, Float>()

    /** Records how far [page]'s search field is open, for the bar on the other side of a swipe. */
    internal fun recordFieldExpansion(page: Int?, value: Float) {
        if (page != null) fieldExpansion[page] = value
    }

    /** How far [page]'s field is open, or null for a page that has never published one. */
    fun fieldExpansionFor(page: Int?): Float? = page?.let { fieldExpansion[it] }

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
