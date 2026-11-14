package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.materialkolor.ktx.toHct
import java.io.File
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.theme.CardConfig
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.koin.core.context.GlobalContext

@RunWith(Parameterized::class)
class WearThemeSurfaceTest(private val round: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun actualWearThemeAndInformationCardUseWearSurfaceTones() {
        val config = GlobalContext.get().get<ThemeConfig>()
        val cards = GlobalContext.get().get<CardConfig>()
        val originalSeed = config.seedColor
        val originalDynamic = config.useDynamicColor
        val originalBackground = config.customBackgroundUri
        val originalBackgroundSeed = config.isUseBackgroundSeedColor
        val originalCustomCards = cards.isCustomBackgroundEnabled
        var originalAlpha = 1f
        lateinit var colors: ColorScheme
        compose.runOnUiThread {
            cards.isCustomBackgroundEnabled = true
            originalAlpha = cards.cardAlpha
            config.customBackgroundUri = null
            config.isUseBackgroundSeedColor = false
        }
        try {
            compose.setContent {
                val configuration = Configuration(LocalConfiguration.current).apply {
                    screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                        if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
                }
                CompositionLocalProvider(LocalConfiguration provides configuration) {
                    WearManagerTheme {
                        colors = MaterialTheme.colorScheme
                        AppScaffold(timeText = {}) {
                            WearList(topContentPadding = 36.dp) { spec ->
                                item {
                                    WearInfoCard(spec, Modifier.heightIn(min = 64.dp).testTag("surface-card"), contentPadding = PaddingValues(16.dp)) {
                                        Text(stringResource(R.string.wear_module_information))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            listOf(false to 0xFF000001.toInt(), false to 0xFF0033CC.toInt(), true to originalSeed).forEach { (dynamic, seed) ->
                listOf(1f, 0.8f, 0.5f).forEach { alpha ->
                    compose.runOnIdle {
                        config.useDynamicColor = dynamic
                        config.seedColor = seed
                        cards.isCustomBackgroundEnabled = true
                        cards.cardAlpha = alpha
                    }
                    compose.runOnIdle {
                        assertEquals(15.0, colors.surfaceContainerLow.copy(alpha = 1f).toHct().tone, 1.0)
                        assertEquals(20.0, colors.surfaceContainer.copy(alpha = 1f).toHct().tone, 1.0)
                        assertEquals(30.0, colors.surfaceContainerHigh.copy(alpha = 1f).toHct().tone, 1.0)
                        assertEquals(alpha, colors.surfaceContainer.alpha, 0.01f)
                    }
                    val pixels = compose.onNodeWithTag("surface-card").captureToImage().toPixelMap()
                    val actual = pixels[pixels.width / 2, (pixels.height * 0.08f).toInt()]
                    val expected = colors.surfaceContainer.compositeOver(Color.Black)
                    assertEquals("The information card must paint its themed container", expected.red, actual.red, 0.03f)
                    assertEquals(expected.green, actual.green, 0.03f)
                    assertEquals(expected.blue, actual.blue, 0.03f)
                    File(compose.activity.getExternalFilesDir(null), "surface-$round-$dynamic-${seed.toUInt().toString(16)}-$alpha.png").outputStream().use {
                        compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
        } finally {
            compose.runOnUiThread {
                config.seedColor = originalSeed
                config.useDynamicColor = originalDynamic
                config.customBackgroundUri = originalBackground
                config.isUseBackgroundSeedColor = originalBackgroundSeed
                cards.cardAlpha = originalAlpha
                cards.isCustomBackgroundEnabled = originalCustomCards
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}")
        fun screens() = listOf(false, true)
    }
}
