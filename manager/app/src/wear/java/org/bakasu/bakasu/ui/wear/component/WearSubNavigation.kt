package org.bakasu.bakasu.ui.wear.component

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.foundation.LocalSwipeToDismissBackgroundScrimColor
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import org.bakasu.bakasu.ui.wear.WearNavigator
import org.bakasu.bakasu.ui.wear.WearRoute
import org.bakasu.bakasu.ui.wear.titleResource
import org.bakasu.bakasu.ui.wear.withPageTransitions

internal val LocalWearSubPageTitle = compositionLocalOf<String?> { null }

/** Feature drafts and running operations stay in the owner; Navigation 3 owns its child pages. */
@Composable
internal fun WearSubNavigation(navigator: WearNavigator, transparentRoot: Boolean = false, content: @Composable (WearRoute) -> Unit) {
    // A transparent root keeps live content (the module WebView) under the navigation layer.
    // The Wear scene strategy fills that layer with the theme's background scrim, which would
    // hide the content, so a transparent root also clears the scrim.
    val scrimColor = if (transparentRoot) Color.Transparent else LocalSwipeToDismissBackgroundScrimColor.current
    CompositionLocalProvider(LocalSwipeToDismissBackgroundScrimColor provides scrimColor) {
        NavDisplay(
            backStack = navigator.backStack,
            modifier = Modifier.fillMaxSize(),
            onBack = navigator::back,
            sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy(isUserSwipeEnabled = Build.VERSION.SDK_INT >= 36 || LocalWearSwipeEnabled.current)),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = withPageTransitions(
                { route ->
                    NavEntry(route) {
                        val page = it as WearRoute
                        val title = if (page == navigator.backStack.first()) {
                            LocalWearSubPageTitle.current
                        } else if (page is WearRoute.WebPrompt) {
                            page.message
                        } else {
                            stringResource(page.titleResource(org.bakasu.bakasu.R.string.home))
                        }
                        CompositionLocalProvider(LocalWearSubPageTitle provides title) { content(page) }
                    }
                },
                { route, page -> if (transparentRoot && route == navigator.backStack.first()) page() else WearBackground { page() } },
            ),
        )
    }
}
