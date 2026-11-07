package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import java.io.File
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.RepositorySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearRepositorySourceLayoutTest(private val round: Boolean, private val fontScale: Float) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun repositoryTitleHasNoCardAndSelectionUsesTheBottomAction() {
        val selected = mutableStateOf<String?>(null)
        var selections = 0
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
                screenWidthDp = 192
                screenHeightDp = 192
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp).background(MaterialTheme.colorScheme.background).testTag("page")) {
                        AppScaffold(timeText = {}) {
                            WearRepositorySourcePage(RepositorySource.KERNEL_SU_URL, selected.value, {}) {
                                selections++
                                selected.value = it
                            }
                        }
                    }
                }
            }
        }
        val title = compose.onNodeWithText(compose.activity.getString(R.string.repo_kernel_su))
        val bounds = title.getUnclippedBoundsInRoot()
        val root = compose.onNodeWithTag("page").getUnclippedBoundsInRoot()
        assertEquals((root.left + root.right).value / 2, (bounds.left + bounds.right).value / 2, 1f)
        val bitmap = compose.onNodeWithTag("page").captureToImage().asAndroidBitmap()
        // The old title card filled this point inside its rounded surface, beside the title text.
        val x = (0.10f * bitmap.width).toInt()
        val y = (((bounds.top + bounds.bottom) / 2 - root.top).value / (root.bottom - root.top).value * bitmap.height).toInt()
        assertEquals(bitmap.getPixel(bitmap.width / 2, 2), bitmap.getPixel(x, y))
        saveScreen("repository-title")
        compose.onNode(hasScrollToIndexAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10000f) }
        val select = compose.onNodeWithText(compose.activity.getString(R.string.repo_select_repository))
        select.assertIsDisplayed()
        val actionBounds = select.getUnclippedBoundsInRoot()
        assertTrue(actionBounds.top + actionBounds.bottom > root.top + root.bottom)
        saveScreen("repository-selection")
        select.performClick()
        compose.runOnIdle {
            assertEquals(1, selections)
            assertEquals(RepositorySource.KERNEL_SU_URL, selected.value)
        }
        select.assertIsNotEnabled()
    }

    private fun saveScreen(name: String) {
        val bitmap = compose.onNodeWithTag("page").captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "$name-$round-$fontScale.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, fontScale={1}")
        fun configurations() = listOf(arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1.3f), arrayOf<Any>(false, 1.3f))
    }
}
