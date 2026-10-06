package org.bakasu.bakasu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.bakasu.bakasu.ui.component.settings.AppBackButton
import org.bakasu.bakasu.ui.navigation.LocalNavigator
import org.bakasu.bakasu.ui.theme.CardConfig
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.theme.blurEffect
import org.bakasu.bakasu.ui.util.LocalSnackbarHost
import org.bakasu.bakasu.ui.util.adaptiveScaffoldWindowInsets
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RepositoryPageScaffold(
    title: String,
    topBarContent: @Composable () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val navigator = LocalNavigator.current
    val theme = koinInject<ThemeConfig>()
    val cards = koinInject<CardConfig>()
    val scroll = scrollBehavior ?: rememberRepositoryScrollBehavior()
    val barColor = if (theme.isEnableBlur) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(cards.cardAlpha)
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            Column(Modifier.blurEffect().background(barColor)) {
                LargeFlexibleTopAppBar(
                    title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                    scrollBehavior = scroll,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )
                topBarContent()
            }
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        snackbarHost = { SwipeableSnackbarHost(hostState = LocalSnackbarHost.current) },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberRepositoryScrollBehavior(
    activationKey: Any? = Unit,
): TopAppBarScrollBehavior {
    val state = rememberTopAppBarState()
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state)
    fun restore() {
        state.heightOffset = 0f
        state.contentOffset = 0f
    }
    LaunchedEffect(activationKey) { restore() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { restore() }
    return behavior
}
