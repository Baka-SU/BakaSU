package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel
import com.resukisu.resukisu.ui.component.wear.WearLoadingScreen
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearScaledItem

@Composable
fun WearManagerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val config = koinInject<ThemeConfig>()
    val viewModel = koinViewModel<SettingsViewModel>()
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val systemDensity = LocalDensity.current
    val density = remember(systemDensity, settings.dpi) {
        if (settings.dpi <= 0) systemDensity
        else Density(settings.dpi / 160f, systemDensity.fontScale)
    }
    val generated = remember(config.seedColor, config.dynamicPaletteStyle, config.dynamicColorSpec) {
        com.materialkolor.dynamicColorScheme(
            seedColor = Color(config.seedColor), isDark = true,
            style = config.dynamicPaletteStyle, specVersion = config.dynamicColorSpec,
        )
    }
    val colors = if (config.useDynamicColor) dynamicColorScheme(context) ?: ColorScheme()
    else ColorScheme(
        primary = generated.primary, primaryDim = generated.inversePrimary,
        primaryContainer = generated.primaryContainer, onPrimary = generated.onPrimary,
        onPrimaryContainer = generated.onPrimaryContainer, secondary = generated.secondary,
        secondaryContainer = generated.secondaryContainer, onSecondary = generated.onSecondary,
        onSecondaryContainer = generated.onSecondaryContainer, tertiary = generated.tertiary,
        tertiaryContainer = generated.tertiaryContainer, onTertiary = generated.onTertiary,
        onTertiaryContainer = generated.onTertiaryContainer,
        surfaceContainer = generated.surfaceContainer, onSurface = generated.onSurface,
        onSurfaceVariant = generated.onSurfaceVariant, background = Color.Black,
    )
    CompositionLocalProvider(LocalDensity provides density) {
        MaterialTheme(colorScheme = colors.copy(background = Color.Black)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) { content() }
        }
    }
}

@Composable
fun WearStartupStatus(error: String? = null) {
    AppScaffold {
        if (error == null) WearLoadingScreen()
        else WearList { spec ->
            item { WearScaledItem(spec) { Text(error) } }
        }
    }
}
