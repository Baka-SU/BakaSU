package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import java.io.File
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.InstalledApp
import org.bakasu.bakasu.domain.model.InstalledAppGroup
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoUiState
import org.bakasu.bakasu.ui.wear.component.WearAutoMarqueeText
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.settings.WearChoicePage
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearMarqueeLayoutTest(private val round: Boolean, private val font: Float) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun moduleDetailTitleAndVersionActuallyScrollAndHonorReducedMotion() {
        val title = "很长的模块名称 A very long module name ".repeat(4)
        val version = "version-2026.11.13-release-candidate-".repeat(3)
        val reduceMotion = mutableStateOf(false)
        val module = InstalledModule("example", title, "Author", version, 1, "", true, false, false, "", false, false, true, null, null, "example", null)
        // Automatic test-clock advancement cancels infinite animations such as marquee.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, font),
                LocalReduceMotion provides reduceMotion.value,
            ) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp)) {
                        AppScaffold(timeText = {}) { WearModuleDetail(module, null, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}) }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        fun layout(text: String): TextLayoutResult {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single()
        }
        fun snapshot(text: String): Bitmap {
            val bounds = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val density = compose.activity.resources.displayMetrics.density
            val left = (bounds.left.value * density).toInt().coerceIn(0, bitmap.width - 1)
            val top = (bounds.top.value * density).toInt().coerceIn(0, bitmap.height - 1)
            val right = (bounds.right.value * density).toInt().coerceIn(left + 1, bitmap.width)
            val bottom = (bounds.bottom.value * density).toInt().coerceIn(top + 1, bitmap.height)
            return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        }
        listOf(title, version).forEach {
            assertEquals(1, layout(it).lineCount)
            assertFalse("Overflowing module text must be measured for marquee, not ellipsized", layout(it).hasVisualOverflow)
        }
        compose.mainClock.autoAdvance = false
        try {
            compose.mainClock.advanceTimeBy(4000)
            val scrolling = listOf(title, version).map(::snapshot)
            compose.mainClock.advanceTimeBy(600)
            listOf(title, version).forEachIndexed { index, text ->
                assertFalse("Both title and version must visibly scroll: index=$index", scrolling[index].sameAs(snapshot(text)))
            }
            compose.runOnIdle { reduceMotion.value = true }
            compose.mainClock.advanceTimeBy(300)
            listOf(title, version).forEach { assertTrue("Reduced motion uses ellipsis", layout(it).hasVisualOverflow) }
            val stopped = listOf(title, version).map(::snapshot)
            compose.mainClock.advanceTimeBy(600)
            listOf(title, version).forEachIndexed { index, text ->
                assertTrue("Reduced motion must stop the text", stopped[index].sameAs(snapshot(text)))
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun overflowingSummariesBecomeSingleLineWhileInformationCardsKeepWrapping() {
        val summary = "A long interactive summary ".repeat(20)
        val information = "Read-only information remains fully wrapped ".repeat(20)
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, font)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp)) {
                        AppScaffold(timeText = {}) {
                            WearList { spec ->
                                item { WearSettingsJumpPageWidget(spec, "Settings", {}, description = summary) }
                                item { WearInfoCard(spec) { androidx.wear.compose.material3.Text(information) } }
                            }
                        }
                    }
                }
            }
        }
        val summaryLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(summary, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(summaryLayouts) }
        assertEquals(1, summaryLayouts.single().lineCount)
        assertFalse(summaryLayouts.single().hasVisualOverflow)
        val informationLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(information, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(informationLayouts) }
        assertTrue(informationLayouts.single().lineCount > 2)
        assertFalse(informationLayouts.single().hasVisualOverflow)
    }

    @Test
    fun longListLabelsStayOnOneLineAndActionsRemainAvailable() {
        val page = mutableStateOf("repository")
        val title = "很长的模块与应用名称 A long module or application name"
        val description = "完整的介绍和当前设置值 A long description that remains readable"
        val module = InstalledModule("example", title, "Author", "v1", 1, description, true, false, false, "", false, false, true, null, null, "example", null)
        val catalog = CatalogModule("example", title, "Author", emptyList(), description, true, 0, "", "", "", "", 1, null, "", emptyList(), "https://example.com")
        val app = InstalledApp("android", title, 1000)
        var clicks = 0
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, font)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp).testTag("marquee-page")) {
                        AppScaffold(timeText = {}) {
                            when (page.value) {
                                "repository" -> WearModuleRepoPage(ModuleRepoUiState(modules = listOf(catalog)), {}, {}, {}, onOpen = { clicks++ })

                                "choice" -> WearChoicePage("Settings", listOf("one" to title), "one", {}) { clicks++ }

                                else -> WearList { spec ->
                                    item {
                                        when (page.value) {
                                            "module" -> WearModuleItem(spec, module, { clicks++ })
                                            "app" -> WearAppItem(spec, InstalledAppGroup(app.uid, app.packageName, listOf(app)), { _, _ -> clicks++ })
                                            "switch" -> WearSettingsSwitchWidget(spec, title, false, { clicks++ }, secondaryLabel = description)
                                            else -> WearSettingsJumpPageWidget(spec, title, { clicks++ }, description = description)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        listOf("repository", "module", "app", "switch", "settings", "choice").forEachIndexed { index, value ->
            compose.runOnIdle { page.value = value }
            val labels = if (value in listOf("app", "choice", "module")) listOf(title) else listOf(title, description)
            labels.forEach { text ->
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                layouts.forEach {
                    assertTrue(if (text == title) it.lineCount == 1 else it.lineCount in 1..2)
                    assertTrue(!it.didOverflowHeight)
                }
            }
            compose.onNodeWithText(title).performClick()
            assertEquals(index + 1, clicks)
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(2000)
            val bitmap = compose.onNodeWithTag("marquee-page").captureToImage().asAndroidBitmap()
            File(compose.activity.getExternalFilesDir(null), "marquee-$value-$round-$font.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            compose.mainClock.autoAdvance = true
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, font={1}")
        fun configurations() = listOf(arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1.3f), arrayOf<Any>(false, 1.3f))
    }
}
