package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import java.io.File
import java.util.Locale
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.viewmodel.SulogLoading
import org.bakasu.bakasu.ui.viewmodel.SulogUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearSulogDisabledNoticeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun onlyTheTrailingButtonEnablesSulogAndEnablingShowsPageLoading() {
        val state = mutableStateOf(SulogUiState(isLoading = false, sulogStatus = "supported"))
        val configuration = Configuration(compose.activity.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
            screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or Configuration.SCREENLAYOUT_ROUND_YES
        }
        val context = compose.activity.createConfigurationContext(configuration)
        var enables = 0
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(error = Color.Red)) {
                    AppScaffold {
                        Box(Modifier.requiredSize(216.dp)) {
                            WearLogsPage(state.value, {}, { enables++ }, {}, onEntry = {}, loading = if (state.value.isRefreshing) SulogLoading.REFRESH else SulogLoading.NONE)
                        }
                    }
                }
            }
        }
        val title = context.getString(R.string.wear_sulog_disabled_title)
        val enable = context.getString(R.string.sulog_enable_action)
        compose.onNodeWithText(title).assertIsDisplayed().assertHasNoClickAction()
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), "sulog-disabled-notice.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        fun redPixels(): Int {
            val pixels = compose.onNodeWithText(enable).captureToImage().toPixelMap()
            var count = 0
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) {
                    val pixel = pixels[x, y]
                    if (pixel.red > 0.8f && pixel.green < 0.2f && pixel.blue < 0.2f) count++
                }
            }
            return count
        }
        val before = redPixels()
        compose.runOnIdle { state.value = state.value.copy(isRefreshing = true) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(enable))
        org.junit.Assert.assertTrue("Refresh must preserve the error-colored enable action", redPixels() >= before * 0.8f)
        compose.runOnIdle { state.value = state.value.copy(isRefreshing = false) }
        compose.onNodeWithText(enable).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, enables)
            state.value = state.value.copy(isEnabling = true)
        }
        compose.onNodeWithText(enable).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.sulog)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.wear_loading)).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(isSulogEnabled = true, isEnabling = false) }
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.onNodeWithText(enable).assertDoesNotExist()
    }
}
