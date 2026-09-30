package com.resukisu.resukisu.ui.component.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwipeToDismissBox

/** A page nested inside one route; Back and swipe-to-dismiss return to that route instead of leaving it. */
@Composable
fun WearSubPage(onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    SwipeToDismissBox(onDismissed = onBack) { isBackground ->
        if (isBackground) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        else content()
    }
}
