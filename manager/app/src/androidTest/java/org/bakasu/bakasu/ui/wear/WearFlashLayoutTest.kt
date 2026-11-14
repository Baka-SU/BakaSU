package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.FlashOperation
import org.bakasu.bakasu.ui.viewmodel.FlashUiState
import org.bakasu.bakasu.ui.wear.component.LocalWearTimeOptions
import org.bakasu.bakasu.ui.wear.component.WearTimeOptions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearFlashLayoutTest(private val round: Boolean, private val timeMode: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun moduleAndBootProgressKeepStatusAndActionsWithoutSectionHeadings() {
        val operation = mutableStateOf<FlashOperation>(FlashOperation.Module("content://test/module.zip"))
        val state = mutableStateOf(FlashUiState(output = "Installing files"))
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalWearTimeOptions provides WearTimeOptions(timeMode)) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        WearFlashPage(operation.value, state.value, false, {}, {})
                    }
                }
            }
        }
        for (target in listOf(FlashOperation.Module("content://test/module.zip"), FlashOperation.Boot(null, ota = false, partition = null))) {
            for (exitCode in listOf(null, 0, 1)) {
                compose.runOnIdle {
                    operation.value = target
                    state.value = FlashUiState(output = "Installing files", exitCode = exitCode, showReboot = exitCode == 0)
                }
                compose.onNodeWithText(compose.activity.getString(R.string.wear_install_status)).assertDoesNotExist()
                compose.onNodeWithText(compose.activity.getString(R.string.wear_result)).assertDoesNotExist()
                val status = compose.activity.getString(
                    when (exitCode) {
                        0 -> R.string.flash_success
                        1 -> R.string.flash_failed
                        else -> if (target is FlashOperation.Module) R.string.wear_installing_module else R.string.flashing
                    },
                )
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(status))
                compose.onNodeWithText(status).assertExists()
                if (exitCode == 1) {
                    compose.onNodeWithText(compose.activity.getString(R.string.wear_labeled_value, compose.activity.getString(R.string.error_code), "1"), useUnmergedTree = true).assertExists()
                } else {
                    compose.onNodeWithText("Installing files", useUnmergedTree = true).assertExists()
                }
                if (exitCode != null) {
                    val save = compose.activity.getString(R.string.save_log)
                    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(save))
                    compose.onNodeWithText(save).assertExists()
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, time={1}")
        fun configurations() = listOf(false, true).flatMap { round -> listOf("time", "title", "hidden").map { arrayOf<Any>(round, it) } }
    }
}
