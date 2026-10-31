package org.bakasu.bakasu.ui.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * The Wear back stack and its rules: a page opens once on top, Back on the first page leaves the
 * app, and pages that need root or SUSFS close when it goes away. [onLeave] runs for the page Back
 * leaves, before it closes; [onChange] runs on every move.
 */
class WearNavigator internal constructor(
    val backStack: NavBackStack<NavKey>,
    private val onChange: () -> Unit,
    private val onLeave: (WearRoute) -> Unit,
    private val onExit: () -> Unit,
) {
    val top: WearRoute? get() = backStack.lastOrNull() as WearRoute?

    fun open(route: WearRoute) {
        onChange()
        if (top != route) backStack.add(route)
    }

    fun back() {
        onChange()
        top?.let(onLeave)
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onExit()
    }

    /** Replaces the top page, so Back from [route] returns to the page below it. */
    fun replace(route: WearRoute) {
        back()
        open(route)
    }

    /** Closes every page once root or SUSFS, which it needs, is no longer available. */
    fun closeUnavailable(fullFeatured: Boolean, susfs: Boolean, rootAvailable: Boolean) {
        val lost = backStack.any {
            (it as WearRoute).needsRoot && !fullFeatured || it == WearRoute.SuSFS && !susfs ||
                it is WearRoute.Operations && !(if (it.page == WearOperationsPage.Home) rootAvailable else fullFeatured)
        }
        if (lost) {
            onChange()
            backStack.retainAll { it == WearRoute.Main }
        }
    }

    /**
     * Leaves an in-app search input: a query shows [results], or updates the results page the input
     * was opened from; a blank query only closes the input.
     */
    fun submitSearch(results: WearRoute, query: String, search: (String) -> Unit) {
        back()
        if (query.isBlank()) return
        search(query.trim())
        open(results)
    }
}

@Composable
internal fun rememberWearNavigator(onChange: () -> Unit, onLeave: (WearRoute) -> Unit, onExit: () -> Unit): WearNavigator {
    val backStack = rememberNavBackStack(WearRoute.Main)
    val change by rememberUpdatedState(onChange)
    val leave by rememberUpdatedState(onLeave)
    val exit by rememberUpdatedState(onExit)
    return remember(backStack) { WearNavigator(backStack, { change() }, { leave(it) }, { exit() }) }
}
