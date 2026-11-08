package org.bakasu.bakasu.ui.wear.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearSwipeActionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun swipeAndHoldRevealWithoutPagingAndCoveredRightSwipeStillPages() {
        lateinit var pager: PagerState
        var clicks = 0
        var deletions = 0
        compose.setContent {
            pager = rememberPagerState(initialPage = 1) { 2 }
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    WearHorizontalPager(pager) { page ->
                        if (page == 0) {
                            Text("Other page")
                        } else {
                            val list = rememberTransformingLazyColumnState()
                            WearList(listState = list) { spec ->
                                item { WearPageHeader(spec, "Actions") }
                                item {
                                    WearSwipeToDelete(spec, list.isScrollInProgress, "Remove row", { deletions++ }) {
                                        Button(onClick = { clicks++ }, modifier = Modifier.fillMaxWidth(), label = { Text("Row") })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Row").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Remove row").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, pager.currentPage)
            assertEquals(0, deletions)
            assertEquals(0, clicks)
        }
        // Start on the visible action, not the translated content's off-screen bounds.
        compose.onNodeWithContentDescription("Remove row").performTouchInput { swipeRight() }
        compose.onNodeWithText("Row").performClick()
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(1, pager.currentPage)
        }
        compose.onNodeWithText("Row").performTouchInput { longClick(durationMillis = 600) }
        compose.onNodeWithContentDescription("Remove row").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, deletions)
            assertEquals(1, clicks)
        }
        compose.onNodeWithText("Row").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(0, pager.currentPage) }
    }
}
