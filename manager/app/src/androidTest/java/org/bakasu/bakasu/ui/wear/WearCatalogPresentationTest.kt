package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
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
    fun metaBadgeCentersOnTheTitleAndWrapsWithoutOverlapping() {
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
        val wrappedTitle = compose.onNodeWithText(title.value, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val wrappedBadge = compose.onNodeWithText("META", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(wrappedBadge.top >= wrappedTitle.bottom || wrappedBadge.left >= wrappedTitle.right)
    }

    @Test
    fun sourceSearchUsesItsOwnQueryAndKeepsAddInTheMenu() {
        val query = mutableStateOf("kernelsu.org")
        var selected: String? = null
        compose.setContent {
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    WearRepositorySourcesPage({}, {}, query.value) { selected = it }
                }
            }
        }
        val name = compose.activity.getString(R.string.repo_kernel_su)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(name))
        compose.onNodeWithText(name).assertIsDisplayed().performClick()
        assertEquals("https://modules.kernelsu.org/", selected)
        compose.onNodeWithText(compose.activity.getString(R.string.repo_add_repository)).assertDoesNotExist()
        compose.runOnIdle { query.value = "does-not-exist" }
        compose.onNodeWithText(compose.activity.getString(R.string.search_no_any_match)).assertExists()
        compose.runOnIdle { query.value = "" }
        compose.onNodeWithText(name).assertExists()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, font={1}")
        fun parameters() = listOf(arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1.3f), arrayOf<Any>(false, 1.3f))
    }
}
