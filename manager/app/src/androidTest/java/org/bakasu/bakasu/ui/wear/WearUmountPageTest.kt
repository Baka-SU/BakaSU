package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.UmountPath
import org.bakasu.bakasu.ui.viewmodel.UmountManagerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearUmountPageTest(private val round: Boolean, private val fontScale: Float) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val entry = UmountPath("/data/adb/modules", 2, true)
    private var state by mutableStateOf(UmountManagerUiState(listOf(entry, UmountPath("/system", 0, false)), isLoading = false))
    private var removed: UmountPath? = null

    @Test
    fun ordinaryTapIsInertAndLongPressRequiresDeleteConfirmation() {
        showPage()
        compose.onNodeWithText(compose.activity.getString(R.string.add_umount_path)).assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(entry.path))
        compose.onNodeWithText(entry.path).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText(entry.path).performTouchInput { longClick() }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.delete)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertNull(removed) }
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithContentDescription(compose.activity.getString(androidx.wear.compose.material3.R.string.wear_m3c_alert_dialog_content_description_confirm_button)).performClick()
        compose.runOnIdle { assertEquals(entry, removed) }
        compose.onNodeWithText(entry.path).assertDoesNotExist()
    }

    @Test
    fun searchKeepsCaseAndUpdatesWithTheSourceCollection() {
        showPage("/data")
        compose.onNodeWithText(entry.path).assertExists()
        compose.onNodeWithText("/system").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(umountPaths = listOf(UmountPath("/DATA", 0, false))) }
        compose.onNodeWithText(compose.activity.getString(R.string.search_no_any_match)).assertExists()
        compose.runOnIdle { state = state.copy(umountPaths = emptyList()) }
        compose.onNodeWithText(compose.activity.getString(R.string.no_any_umount_path)).assertExists()
    }

    private fun showPage(query: String = "") {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        WearUmountPage(state, {}, {}, query) {
                            removed = it
                            state = state.copy(umountPaths = state.umountPaths - it)
                        }
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, fontScale={1}")
        fun configurations() = listOf(arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1.3f), arrayOf<Any>(false, 1.3f))
    }
}
