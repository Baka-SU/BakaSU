package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.theme.CardConfig
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.viewmodel.SettingsViewModel
import org.bakasu.bakasu.ui.viewmodel.WearPreferencesViewModel
import org.bakasu.bakasu.ui.wear.component.WearBackground
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearLoadingScreen
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone
import org.bakasu.bakasu.ui.wear.component.WearTimeText
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

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
        if (preferences.shape != "round" && preferences.shape != "square") {
            systemConfiguration
        } else {
            Configuration(systemConfiguration).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (preferences.shape == "round") Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
        }
    }
    val systemDensity = LocalDensity.current
    val density = remember(systemDensity, settings.dpi) {
        if (settings.dpi <= 0) {
            systemDensity
        } else {
            Density(settings.dpi / 160f, systemDensity.fontScale)
        }
    }
    val generated = remember(config.seedColor, config.dynamicPaletteStyle, config.dynamicColorSpec) {
        com.materialkolor.dynamicColorScheme(
            seedColor = Color(config.seedColor),
            isDark = true,
            style = config.dynamicPaletteStyle,
            specVersion = config.dynamicColorSpec,
        )
    }
    // A custom accent maps the generated dark scheme onto the Wear roles exactly as the Wear
    // dynamicColorScheme maps the system palette: accents from the fixed colors, containers, surfaces,
    // outlines and errors from the dark roles. Every role comes from the same seed, so each on-color
    // keeps its pairing and contrast.
    // Remembered, so settings updates such as dragging the DPI slider do not rebuild the scheme and
    // recompose every themed component.
    val colors = remember(config.useDynamicColor, generated, context) {
        val scheme = if (config.useDynamicColor) {
            dynamicColorScheme(context) ?: ColorScheme()
        } else {
            ColorScheme(
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
        }
        // Wear screens keep a pure black background whichever scheme is used. The navigation host
        // makes this role transparent so its backdrop image is not covered by the scene's
        // swipe-dismiss box, and restores it per entry; see WearManager.pageBaseDecorator.
        scheme.copy(background = Color.Black)
    }
    // Container colors follow the card transparency so a custom background shows through them,
    // the same rule the phone applies per row in SettingsBaseWidget. It has to happen here rather
    // than per call site: Wear components take their containers from scheme roles, including the
    // ones inside the library (slider, swipe-to-reveal, switch track). Accent fills that the phone
    // also leaves solid stay solid — `primary` for high-emphasis buttons, `errorContainer` for
    // error surfaces, and `background` for dialogs. Derived outside the remember above so the
    // scheme itself is still only generated when the seed or the dynamic-color switch changes.
    val cardConfig = koinInject<CardConfig>()
    val alpha = cardConfig.cardAlpha
    val themedColors = remember(colors, alpha) {
        if (alpha >= 1f) {
            colors
        } else {
            colors.copy(
                surfaceContainerLow = colors.surfaceContainerLow.copy(alpha = alpha),
                surfaceContainer = colors.surfaceContainer.copy(alpha = alpha),
                surfaceContainerHigh = colors.surfaceContainerHigh.copy(alpha = alpha),
                primaryContainer = colors.primaryContainer.copy(alpha = alpha),
                secondaryContainer = colors.secondaryContainer.copy(alpha = alpha),
                tertiaryContainer = colors.tertiaryContainer.copy(alpha = alpha),
            )
        }
    }
    CompositionLocalProvider(LocalDensity provides density, LocalConfiguration provides configuration) {
        MaterialTheme(colorScheme = themedColors, motionScheme = MotionScheme.expressive()) {
            // The selected image and dim layer belong to the shared page background.
            WearBackground { content() }
        }
    }
}

@Composable
fun WearStartupStatus(error: String? = null) {
    AppScaffold(timeText = { WearTimeText() }, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        if (error == null) {
            WearLoadingScreen()
        } else {
            WearList { spec ->
                item { WearPageHeader(spec, stringResource(R.string.app_name)) }
                item { WearStatusItem(spec, Icons.TwoTone.Error, error, tone = WearStatusTone.ERROR) }
            }
        }
    }
}
