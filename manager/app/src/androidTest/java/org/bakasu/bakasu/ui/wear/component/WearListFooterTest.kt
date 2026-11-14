package org.bakasu.bakasu.ui.wear.component

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.wear.compose.foundation.LocalReduceMotion
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
class WearListFooterTest(private val round: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun confirmRetainsItsEnabledStateAndCallbackAcrossRefresh() {
        val enabled = mutableStateOf(false)
        val refreshing = mutableStateOf(false)
        lateinit var listState: TransformingLazyColumnState
        var confirmations = 0
        var backs = 0
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        listState = rememberTransformingLazyColumnState()
                        WearList(
                            listState = listState,
                            onBack = { backs++ },
                            onConfirm = { confirmations++ },
                            confirmEnabled = enabled.value,
                            isRefreshing = refreshing.value,
                        ) { spec ->
                            items(12) { WearChip(spec, stringResource(R.string.readme)) }
                        }
                    }
                }
            }
        }
        fun scrollToFooter() {
            compose.runOnIdle { listState.requestScrollToItem(listState.layoutInfo.totalItemsCount - 1) }
        }
        val confirm = compose.activity.getString(R.string.confirm)
        scrollToFooter()
        compose.onNodeWithContentDescription(confirm).assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle { enabled.value = true }
        compose.onNodeWithContentDescription(confirm).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, confirmations)
            assertEquals(0, backs)
            refreshing.value = true
        }
        scrollToFooter()
        if (round) {
            compose.onNodeWithContentDescription(confirm).assertDoesNotExist()
        } else {
            compose.onNodeWithContentDescription(confirm).assertIsDisplayed().assertIsNotEnabled()
        }
        compose.runOnIdle { refreshing.value = false }
        scrollToFooter()
        compose.onNodeWithContentDescription(confirm).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(2, confirmations)
            assertEquals(0, backs)
        }
    }

    @Test
    fun backToTopHonorsReducedMotion() {
        lateinit var listState: TransformingLazyColumnState
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalReduceMotion provides true) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        listState = rememberTransformingLazyColumnState()
                        WearList(listState = listState, backToTop = true) { spec ->
                            items(20) { WearChip(spec, stringResource(R.string.readme)) }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { listState.requestScrollToItem(listState.layoutInfo.totalItemsCount - 1) }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.scroll_to_top)).assertIsDisplayed().assertIsEnabled()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.scroll_to_top)).performClick()
            compose.mainClock.advanceTimeBy(32)
            compose.runOnIdle { assertTrue("Reduced motion returns to the top without a scroll animation", listState.isAtWearListStart(4)) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}")
        fun configurations() = listOf(arrayOf(true), arrayOf(false))
    }
}
