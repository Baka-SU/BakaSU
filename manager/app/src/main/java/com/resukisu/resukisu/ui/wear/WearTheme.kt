package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import com.resukisu.resukisu.ui.viewmodel.WearPreferencesViewModel
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.res.stringResource
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Error
import com.resukisu.resukisu.ui.component.wear.WearStatusTone
import com.resukisu.resukisu.ui.component.wear.WearStatusItem
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.wear.WearBackground
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearLoadingScreen
import com.resukisu.resukisu.ui.component.wear.WearScaledItem
import com.resukisu.resukisu.ui.component.wear.WearTimeText
import com.resukisu.resukisu.ui.component.wear.LocalWearPageAnimation
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
    val preferences by koinViewModel<WearPreferencesViewModel>().state.collectAsStateWithLifecycle()
    // A chosen screen shape replaces the device's round flag, which Wear Compose layouts follow.
    val systemConfiguration = LocalConfiguration.current
    val configuration = remember(systemConfiguration, preferences.shape) {
        if (preferences.shape != "round" && preferences.shape != "square") systemConfiguration
        else Configuration(systemConfiguration).apply {
            screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                if (preferences.shape == "round") Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
        }
    }
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
    CompositionLocalProvider(LocalDensity provides density, LocalConfiguration provides configuration,
        LocalWearPageAnimation provides preferences.pageAnimation) {
        MaterialTheme(colorScheme = colors, motionScheme = MotionScheme.expressive()) {
            // Keep the root black; neutral content cards render the selected image and dim layer.
            // A language change swaps the texts in place; the content fades back in over it.
            val locales = LocalConfiguration.current.locales
            val motion = MaterialTheme.motionScheme
            val reduceMotion = LocalReduceMotion.current
            val fade = remember { Animatable(1f) }
            var shownLocales by remember { mutableStateOf(locales) }
            LaunchedEffect(locales, reduceMotion) {
                if (locales != shownLocales) {
                    shownLocales = locales
                    if (!reduceMotion) {
                        fade.snapTo(0f)
                        fade.animateTo(1f, motion.fastEffectsSpec())
                    } else fade.snapTo(1f)
                } else if (reduceMotion) {
                    fade.snapTo(1f)
                }
            }
            WearBackground { Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) { content() } }
        }
    }
}

@Composable
fun WearStartupStatus(error: String? = null) {
    AppScaffold(timeText = { WearTimeText() }, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        if (error == null) WearLoadingScreen()
        else WearList { spec ->
            item { WearPageHeader(spec, null, stringResource(R.string.app_name)) }
            item { WearStatusItem(spec, Icons.TwoTone.Error, error, tone = WearStatusTone.ERROR) }
        }
    }
}
