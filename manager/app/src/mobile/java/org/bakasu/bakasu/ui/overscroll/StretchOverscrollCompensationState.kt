package org.bakasu.bakasu.ui.overscroll

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.LayoutCoordinates

/**
 * Stretch information published by the vendored platform overscroll effect.
 *
 * Compose can retain child graphics layers and record them separately from the parent overscroll
 * RenderNode. The values therefore remain published for the lifetime of the active stretch instead
 * of only while the parent records drawContent().
 */
@Stable
class StretchOverscrollCompensationState {
    private val horizontalAxes = linkedMapOf<Any, StretchOverscrollAxis>()
    private val verticalAxes = linkedMapOf<Any, StretchOverscrollAxis>()
    private val versionState = mutableIntStateOf(0)

    // publishCompensation() runs on every draw pass even with no stretch, so the version must
    // advance only when the published amounts actually change, otherwise the draw subscribers
    // would invalidate themselves and loop forever at rest.
    private var lastHorizontalAmount: Float? = null
    private var lastVerticalAmount: Float? = null

    val version: Int by versionState

    val horizontal: StretchOverscrollAxis?
        get() = horizontalAxes.values.lastOrNull()

    val vertical: StretchOverscrollAxis?
        get() = verticalAxes.values.lastOrNull()

    internal fun update(
        owner: Any,
        horizontal: StretchOverscrollAxis?,
        vertical: StretchOverscrollAxis?,
    ) {
        horizontalAxes.update(owner, horizontal)
        verticalAxes.update(owner, vertical)
        bumpIfChanged()
    }

    internal fun clear(owner: Any) {
        horizontalAxes.remove(owner)
        verticalAxes.remove(owner)
        bumpIfChanged()
    }

    private fun bumpIfChanged() {
        val h = horizontal?.amount
        val v = vertical?.amount
        if (h != lastHorizontalAmount || v != lastVerticalAmount) {
            lastHorizontalAmount = h
            lastVerticalAmount = v
            versionState.intValue++
        }
    }
}

private fun LinkedHashMap<Any, StretchOverscrollAxis>.update(
    owner: Any,
    axis: StretchOverscrollAxis?,
) {
    remove(owner)
    if (axis != null) put(owner, axis)
}

class StretchOverscrollAxis internal constructor(
    val amount: Float,
    val coordinates: LayoutCoordinates,
)
