package org.bakasu.bakasu.ui.component

import android.os.Environment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.component.settings.AppBackButton
import org.bakasu.bakasu.ui.theme.CardConfig
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.theme.blurEffect
import org.bakasu.bakasu.ui.theme.blurSource
import org.bakasu.bakasu.ui.theme.monospaceFontFamily
import org.bakasu.bakasu.ui.util.LocalSnackbarHost
import org.bakasu.bakasu.ui.util.adaptiveScaffoldWindowInsets
import org.bakasu.bakasu.ui.util.showReplacingSnackbar
import org.bakasu.bakasu.ui.viewmodel.FlashState
import org.bakasu.bakasu.ui.viewmodel.FlashingStatus
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashOutputScreen(
    state: FlashState,
    logFilePrefix: String,
    onBack: () -> Unit,
    onReboot: () -> Unit,
    logContent: String = state.output,
    headerContent: (@Composable () -> Unit)? = null,
) {
    val snackbarHost = LocalSnackbarHost.current
    val savedTemplate = stringResource(R.string.log_saved)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(
            initialHeightOffset = -154f,
            initialHeightOffsetLimit = -154f, // from debugger
        ),
    )
    val canGoBack = state.status != FlashingStatus.FLASHING

    Scaffold(
        topBar = {
            FlashOutputTopBar(state.status, { if (canGoBack) onBack() }, {
                scope.launch {
                    val date = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault()).format(Date())
                    val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "${logFilePrefix}_$date.log")
                    file.writeText(logContent)
                    snackbarHost.showReplacingSnackbar(savedTemplate.format(file.absolutePath))
                }
            }, scrollBehavior)
        },
        floatingActionButton = {
            if (state.showReboot) {
                ExtendedFloatingActionButton(onClick = onReboot, icon = {
                    Icon(Icons.TwoTone.Refresh, contentDescription = stringResource(R.string.reboot))
                }, text = { Text(stringResource(R.string.reboot)) })
            }
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackbarHost) },
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { padding ->
        KeyEventBlocker { it.key == Key.VolumeDown || it.key == Key.VolumeUp }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurSource(),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding() + 12.dp))
            headerContent?.invoke()
            Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState)) {
                LaunchedEffect(state.output) { scrollState.animateScrollTo(scrollState.maxValue) }
                CatalogCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        state.output,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = monospaceFontFamily(),
                    )
                }
            }
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlashOutputTopBar(status: FlashingStatus, onBack: () -> Unit, onSave: () -> Unit, scrollBehavior: TopAppBarScrollBehavior) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val containerColor = if (themeConfig.isEnableBlur) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
    val color = when (status) {
        FlashingStatus.FLASHING -> MaterialTheme.colorScheme.primary
        FlashingStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
        FlashingStatus.FAILED -> MaterialTheme.colorScheme.error
    }
    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(),
        title = {
            Text(
                stringResource(status.resId),
                color = color,
            )
        },
        navigationIcon = { AppBackButton(onClick = onBack) },
        actions = { IconButton(onClick = onSave) { Icon(Icons.TwoTone.Save, stringResource(R.string.save_log)) } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor, scrolledContainerColor = containerColor),
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior,
    )
}
