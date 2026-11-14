package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import java.io.File
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.wear.component.LocalWearToast
import org.bakasu.bakasu.ui.wear.component.WearToastHost
import org.bakasu.bakasu.ui.wear.component.WearToastState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearToastStyleTest(private val round: Boolean, private val reduceMotion: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun curvedSelectionWarnsOnlyOnSquareScreensAndDoesNotApplyBeforeConfirmation() {
        val choices = mutableListOf<String>()
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalReduceMotion provides reduceMotion) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        WearToastSelectionPage("standard", {}, choices::add)
                    }
                }
            }
        }
        val curved = compose.activity.getString(R.string.wear_toast_curved)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(curved))
        compose.onNodeWithText(curved).performClick()
        if (round) {
            compose.runOnIdle { assertEquals(listOf("round"), choices) }
            compose.onNodeWithText(compose.activity.getString(R.string.wear_toast_square_warning)).assertDoesNotExist()
        } else {
            compose.onNodeWithText(compose.activity.getString(R.string.wear_toast_square_warning)).assertExists()
            compose.runOnIdle { assertEquals(emptyList<String>(), choices) }
        }
    }

    @Test
    fun explicitCurvedPreferenceUsesAnArcForShortMessagesOnEitherScreenShape() {
        lateinit var toast: WearToastState
        val message = compose.activity.getString(R.string.wear_theme_color_applied)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 192
                screenHeightDp = 192
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalReduceMotion provides reduceMotion) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp)) {
                        WearToastHost(style = "round") { toast = LocalWearToast.current }
                    }
                }
            }
        }
        compose.runOnIdle { toast.show(message) }
        compose.onNodeWithContentDescription(message).assertIsDisplayed()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "toast-$round-$reduceMotion.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val long = List(4) { compose.activity.getString(R.string.wear_download_record_missing) }.joinToString(" ")
        compose.runOnIdle { toast.show(long) }
        compose.onNodeWithText(long).assertIsDisplayed()
        compose.onNodeWithContentDescription(long).assertDoesNotExist()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0},reduceMotion={1}")
        fun parameters() = listOf(arrayOf(true, false), arrayOf(false, false), arrayOf(true, true), arrayOf(false, true))
    }
}
