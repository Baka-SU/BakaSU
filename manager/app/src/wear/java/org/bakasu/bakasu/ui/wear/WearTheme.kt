package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.Text
import com.materialkolor.PaletteStyle
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import com.materialkolor.palettes.TonalPalette
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.theme.BackgroundManager
import org.bakasu.bakasu.ui.theme.BackgroundRenderState
import org.bakasu.bakasu.ui.theme.CardConfig
import org.bakasu.bakasu.ui.theme.LocalBackgroundRenderState
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.theme.extractSeedColor
import org.bakasu.bakasu.ui.viewmodel.SettingsViewModel
import org.bakasu.bakasu.ui.viewmodel.WearPreferencesViewModel
import org.bakasu.bakasu.ui.wear.component.LocalWearSwipeEnabled
import org.bakasu.bakasu.ui.wear.component.LocalWearTimeOptions
import org.bakasu.bakasu.ui.wear.component.WearBackground
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearLoadingScreen
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone
import org.bakasu.bakasu.ui.wear.component.WearTimeOptions
import org.bakasu.bakasu.ui.wear.component.WearTimeText
import org.bakasu.bakasu.ui.wear.component.WearToastHost
import org.bakasu.bakasu.ui.wear.component.WearToastState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun wearThemeSeedColor(config: ThemeConfig, backgroundSeedColor: Int): Int = when {
    config.useDynamicColor && config.isUseBackgroundSeedColor && backgroundSeedColor != 0 -> backgroundSeedColor
    config.useDynamicColor && Build.VERSION.SDK_INT >= 31 -> colorResource(android.R.color.system_accent1_500).toArgb()
    config.useDynamicColor -> config.monetCompatSeedColor
    else -> config.seedColor
}

@Composable
fun WearManagerTheme(toastState: WearToastState, content: @Composable () -> Unit) {
    val config = koinInject<ThemeConfig>()
    val viewModel = koinViewModel<SettingsViewModel>()
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by koinViewModel<WearPreferencesViewModel>().state.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(view, preferences.keepScreenOn) {
        view.keepScreenOn = preferences.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    val systemHaptics = LocalHapticFeedback.current
    val haptics = remember(systemHaptics, preferences.longPressHaptics) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (preferences.longPressHaptics || hapticFeedbackType != HapticFeedbackType.LongPress) {
                    systemHaptics.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    }
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
    val backgroundUri = config.customBackgroundUri
    val backgroundManager = koinInject<BackgroundManager>()
    var backgroundImage by remember(backgroundUri) { mutableStateOf<Drawable?>(null) }
    val backgroundState = remember(backgroundUri) {
        BackgroundRenderState().apply { seedColor = config.backgroundSeedColor }
    }
    LaunchedEffect(backgroundUri, backgroundImage) {
        if (backgroundState.seedColor != 0) return@LaunchedEffect
        val image = backgroundImage ?: return@LaunchedEffect
        val seed = image.toBitmap().extractSeedColor()
        if (config.customBackgroundUri != backgroundUri) return@LaunchedEffect
        backgroundManager.saveBackgroundSeedColor(seed)
        backgroundState.seedColor = seed
    }
    val seed = wearThemeSeedColor(config, backgroundState.seedColor)
    val style = config.dynamicPaletteStyle
    val colorSpec = config.dynamicColorSpec
    val colors = remember(seed, style, colorSpec) {
        val generated = com.materialkolor.dynamicColorScheme(
            seedColor = Color(seed),
            isDark = true,
            style = style,
            specVersion = colorSpec,
        )
        // Wear's card tones need a visible seed tint; Material's neutral roles can be nearly gray.
        // Keep the chosen palette's neutral hue and preserve explicitly monochrome schemes.
        val surface = generated.surfaceContainer.toHct()
        val seedChroma = (Color(seed).toHct().chroma / 3).coerceAtMost(24.0)
        val surfaces = TonalPalette.fromHueAndChroma(
            surface.hue,
            if (style == PaletteStyle.Monochrome) surface.chroma else maxOf(surface.chroma, seedChroma),
        )
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
            surfaceContainerLow = Color(surfaces.tone(15)),
            surfaceContainer = Color(surfaces.tone(20)),
            surfaceContainerHigh = Color(surfaces.tone(30)),
            onSurface = generated.onSurface, onSurfaceVariant = generated.onSurfaceVariant,
            outline = generated.outline, outlineVariant = generated.outlineVariant,
            background = Color.Black, onBackground = generated.onBackground,
            error = generated.error, errorDim = generated.errorContainer.toHct().withTone(68.0).toColor(),
            errorContainer = generated.errorContainer, onError = generated.onError,
            onErrorContainer = generated.onErrorContainer,
        )
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
    // Only initial background preparation may delay content. Later image changes keep the
    // navigation, ViewModels and open dialogs composed while their colors update.
    var contentShown by remember { mutableStateOf(false) }
    CompositionLocalProvider(
        LocalDensity provides density,
        LocalConfiguration provides configuration,
        LocalWearSwipeEnabled provides settings.enableSwipeDismiss,
        LocalWearTimeOptions provides WearTimeOptions(preferences.timeDisplay, preferences.immersiveTime),
        LocalHapticFeedback provides haptics,
        LocalBackgroundRenderState provides backgroundState,
    ) {
        MaterialTheme(colorScheme = themedColors, typography = wearTypography(config.isHighContrastMode), motionScheme = MotionScheme.expressive()) {
            // Keep an activity-level backdrop for surfaces outside Navigation 3. Each navigation
            // entry renders its own backdrop as well, so outgoing and incoming scenes transition
            // with independent image layers.
            WearBackground(onImageLoaded = { backgroundImage = it }) {
                WearToastHost(toastState, style = preferences.toastStyle) {
                    // Older installs prepare their saved background color before the first content is shown.
                    if (!contentShown && config.useDynamicColor && config.isUseBackgroundSeedColor && config.customBackgroundUri != null && backgroundState.seedColor == 0) {
                        WearLoadingScreen()
                    } else {
                        SideEffect { contentShown = true }
                        content()
                    }
                }
            }
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
                item { WearStatusItem(spec, Icons.Rounded.Error, error, tone = WearStatusTone.ERROR) }
            }
        }
    }
}

@Composable
private fun wearTypography(highContrast: Boolean): androidx.wear.compose.material3.Typography {
    val original = MaterialTheme.typography
    if (!highContrast) return original
    fun TextStyle.withContrast() = copy(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(1.5f, 1.5f), 0f))
    return original.copy(
        displayLarge = original.displayLarge.withContrast(),
        displayMedium = original.displayMedium.withContrast(),
        displaySmall = original.displaySmall.withContrast(),
        titleLarge = original.titleLarge.withContrast(),
        titleMedium = original.titleMedium.withContrast(),
        titleSmall = original.titleSmall.withContrast(),
        labelLarge = original.labelLarge.withContrast(),
        labelMedium = original.labelMedium.withContrast(),
        labelSmall = original.labelSmall.withContrast(),
        bodyLarge = original.bodyLarge.withContrast(),
        bodyMedium = original.bodyMedium.withContrast(),
        bodySmall = original.bodySmall.withContrast(),
        bodyExtraSmall = original.bodyExtraSmall.withContrast(),
        numeralExtraLarge = original.numeralExtraLarge.withContrast(),
        numeralLarge = original.numeralLarge.withContrast(),
        numeralMedium = original.numeralMedium.withContrast(),
        numeralSmall = original.numeralSmall.withContrast(),
        numeralExtraSmall = original.numeralExtraSmall.withContrast(),
    )
}
