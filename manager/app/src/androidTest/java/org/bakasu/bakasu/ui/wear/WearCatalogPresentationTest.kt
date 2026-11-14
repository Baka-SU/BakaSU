package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.KernelVersion
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.HomeDashboardState
import org.bakasu.bakasu.domain.model.HomeSystemInfo
import org.bakasu.bakasu.domain.model.KernelStatus
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearCatalogPresentationTest(private val round: Boolean, private val fontScale: Float) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun metaBadgeCentersOnTheTitleAndStaysOutsideTheMarqueeViewport() {
        val title = mutableStateOf("Module")
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp)) {
                        AppScaffold(timeText = {}) {
                            WearList {
                                item { WearSettingsJumpPageWidget(it, title.value, {}, titleBadge = "META") }
                            }
                        }
                    }
                }
            }
        }
        val titleBounds = compose.onNodeWithText(title.value, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val badgeBounds = compose.onNodeWithText("META", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((titleBounds.top + titleBounds.bottom).value / 2, (badgeBounds.top + badgeBounds.bottom).value / 2, 2f)
        compose.runOnIdle { title.value = "A module name long enough to wrap onto a second line" }
        // A marquee measures the full text outside its viewport. Compare its visible bounds.
        val wrappedTitle = compose.onNodeWithText(title.value, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val wrappedBadge = compose.onNodeWithText("META", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(wrappedBadge.top >= wrappedTitle.bottom || wrappedBadge.left >= wrappedTitle.right)
    }

    @Test
    fun homeShowsNonArm64ArchitectureAlongsideBothModeBadges() {
        val architecture = mutableStateOf("armv8l")
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp)) {
                        AppScaffold(timeText = {}) {
                            WearHomePage(
                                HomeDashboardState(
                                    systemStatus = KernelStatus(ksuVersion = 1, kernelVersion = KernelVersion(6, 1, 0), isRootAvailable = true, lkmMode = true, isLateLoadMode = true),
                                    systemInfo = HomeSystemInfo(architecture = architecture.value),
                                    isInitialDataLoaded = true,
                                ),
                                null,
                                {},
                                {},
                                onInstall = {},
                            )
                        }
                    }
                }
            }
        }
        val title = compose.activity.getString(R.string.home_working)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(title))
        val titleBounds = compose.onNodeWithText(title, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val badgeScroll = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange), useUnmergedTree = true)
        listOf(compose.activity.getString(R.string.wear_mode_lkm), "armv8l").forEachIndexed { index, text ->
            if (index > 0) badgeScroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(1000f, 0f) }
            val currentTitle = compose.onNodeWithText(title, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val badge = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals("Every badge stays on the title row", (currentTitle.top.value + currentTitle.bottom.value) / 2, (badge.top.value + badge.bottom.value) / 2, 2f)
            assertTrue("Badges never overlap the fixed title", badge.left >= currentTitle.right)
        }
        val after = compose.onNodeWithText(title, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((titleBounds.right - titleBounds.left).value, (after.right - after.left).value, 2f)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(compose.activity.getExternalFilesDir(null), "home-badges-$round-$fontScale.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle { architecture.value = "aarch64" }
        compose.onNodeWithText("aarch64", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun sourceManagementStillOpensRepositoryDetails() {
        var selected: String? = null
        compose.setContent {
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    WearRepositorySourcesPage({}, {}) { selected = it }
                }
            }
        }
        val name = compose.activity.getString(R.string.repo_kernel_su)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(name))
        compose.onNodeWithText(name).assertIsDisplayed().performClick()
        assertEquals("https://modules.kernelsu.org/", selected)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, font={1}")
        fun parameters() = listOf(arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1.3f), arrayOf<Any>(false, 1.3f))
    }
}
