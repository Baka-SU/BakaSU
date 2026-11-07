package org.bakasu.bakasu.ui.wear.component

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearRefreshLayoutTest(private val round: Boolean, private val fontScale: Float) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun refreshFollowsTheTitleAndStaysVisibleWithoutChangingTheScrolledItem() {
        val resources = compose.activity.resources
        var refreshing by mutableStateOf(false)
        var titleInTime by mutableStateOf(false)
        lateinit var state: TransformingLazyColumnState
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 192
                screenHeightDp = 192
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalWearTimeOptions provides WearTimeOptions(if (titleInTime) "title" else "time"),
            ) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp)) {
                        AppScaffold(timeText = {}) {
                            state = rememberTransformingLazyColumnState()
                            WearList(isRefreshing = refreshing, listState = state) { spec ->
                                item { WearPageHeader(spec, stringResource(R.string.umount_path_manager)) }
                                items(20) { WearChip(spec, stringResource(R.string.readme)) }
                            }
                        }
                    }
                }
            }
        }
        val loading = resources.getString(R.string.wear_loading)
        compose.runOnIdle { refreshing = true }
        val indicator = compose.onNodeWithContentDescription(loading).assertIsDisplayed().getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText(resources.getString(R.string.umount_path_manager)).getUnclippedBoundsInRoot()
        val row = compose.onAllNodesWithText(resources.getString(R.string.readme))[0].getUnclippedBoundsInRoot()
        assertTrue("Indicator must follow the whole title", indicator.top >= title.bottom)
        assertTrue("Indicator must not cover the first row", indicator.bottom <= row.top)
        compose.runOnIdle { titleInTime = true }
        compose.onNodeWithText(resources.getString(R.string.umount_path_manager)).assertDoesNotExist()
        compose.onNodeWithContentDescription(loading).assertIsDisplayed()
        compose.runOnIdle { refreshing = false }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(15)
        val anchor = compose.runOnIdle { state.anchorItemIndex }
        compose.runOnIdle { refreshing = true }
        compose.onNodeWithContentDescription(loading).assertIsDisplayed()
        compose.runOnIdle { assertEquals(anchor, state.anchorItemIndex) }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, fontScale={1}")
        fun configurations() = listOf(arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1.3f), arrayOf<Any>(false, 1.3f))
    }
}
