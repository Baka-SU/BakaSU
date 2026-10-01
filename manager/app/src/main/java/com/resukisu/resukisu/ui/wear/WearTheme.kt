package com.resukisu.resukisu.ui.wear

import androidx.compose.ui.res.stringResource
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Error
import com.resukisu.resukisu.ui.component.wear.WearStatusTone
import com.resukisu.resukisu.ui.component.wear.WearStatusItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import coil.compose.AsyncImage
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearLoadingScreen
import com.resukisu.resukisu.ui.component.wear.WearScaledItem
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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
    // A custom accent maps the generated dark scheme onto the Wear roles exactly as the Wear
    // dynamicColorScheme maps the system palette: accents from the fixed colors, containers, surfaces,
    // outlines and errors from the dark roles. Every role comes from the same seed, so each on-color
    // keeps its pairing and contrast.
    // Remembered, so settings updates such as dragging the DPI slider do not rebuild the scheme and
    // recompose every themed component.
    val colors = remember(config.useDynamicColor, generated, context) {
        val scheme = if (config.useDynamicColor) dynamicColorScheme(context) ?: ColorScheme()
        else ColorScheme(
            primary = generated.primaryFixed, primaryDim = generated.primaryFixedDim,
            primaryContainer = generated.primaryContainer, onPrimary = generated.onPrimaryFixed,
            onPrimaryContainer = generated.onPrimaryContainer,
            secondary = generated.secondaryFixed, secondaryDim = generated.secondaryFixedDim,
            secondaryContainer = generated.secondaryContainer, onSecondary = generated.onSecondaryFixed,
            onSecondaryContainer = generated.onSecondaryContainer,
            tertiary = generated.tertiaryFixed, tertiaryDim = generated.tertiaryFixedDim,
            tertiaryContainer = generated.tertiaryContainer, onTertiary = generated.onTertiaryFixed,
            onTertiaryContainer = generated.onTertiaryContainer,
            surfaceContainerLow = generated.surfaceContainerLow, surfaceContainer = generated.surfaceContainer,
            surfaceContainerHigh = generated.surfaceContainerHigh,
            onSurface = generated.onSurface, onSurfaceVariant = generated.onSurfaceVariant,
            outline = generated.outline, outlineVariant = generated.outlineVariant,
            background = Color.Black, onBackground = generated.onBackground,
            error = generated.error, errorDim = generated.errorContainer.toHct().withTone(68.0).toColor(),
            errorContainer = generated.errorContainer, onError = generated.onError,
            onErrorContainer = generated.onErrorContainer,
        )
        // Wear screens keep a pure black background whichever scheme is used.
        scheme.copy(background = Color.Black)
    }
    CompositionLocalProvider(LocalDensity provides density) {
        MaterialTheme(colorScheme = colors) {
            // The custom background is the global backdrop: black base, the image, then a dark
            // dim overlay (backgroundDim). Surfaces (cards, buttons) sit opaque on top of it.
            WearGlobalBackground(config) { content() }
        }
    }
}

/** A full-screen backdrop of the black base plus the custom background image and its dim layer. */
@Composable
private fun WearGlobalBackground(config: ThemeConfig, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        config.customBackgroundUri?.let { uri ->
            AsyncImage(
                model = uri, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Same dimming as the phone background: a black layer at the backgroundDim alpha.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = config.backgroundDim)))
        }
        content()
    }
}

@Composable
fun WearStartupStatus(error: String? = null) {
    AppScaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        if (error == null) WearLoadingScreen()
        else WearList { spec ->
            item { WearPageHeader(spec, null, stringResource(R.string.app_name)) }
            item { WearStatusItem(spec, Icons.TwoTone.Error, error, tone = WearStatusTone.ERROR) }
        }
    }
}
