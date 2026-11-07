package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.domain.model.ModuleUpdateMetadata
import org.bakasu.bakasu.ui.wear.component.WearList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearModuleStateTest(private val round: Boolean) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val module = InstalledModule(
        id = "module", name = "Module with a long name", author = "Author", version = "1", versionCode = 1,
        description = "", enabled = true, update = true, remove = false, updateJson = "",
        hasWebUi = false, hasActionScript = false, metamodule = false,
        actionIconPath = null, webUiIconPath = null, dirId = "module", moduleUpdate = null,
    )

    @Test
    fun pendingUpdateStaysReadableWithLongVersionAndMetaAndStillOpensDetails() {
        val installed = mutableStateOf(module.copy(version = "version-".repeat(12), metamodule = true))
        var opened: String? = null
        show {
            WearList { spec -> item { WearModuleItem(spec, installed.value, { opened = it }) } }
        }
        val status = compose.activity.getString(R.string.reboot_to_apply)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(status, useUnmergedTree = true).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow)
        compose.onNodeWithText(compose.activity.getString(R.string.wear_meta_badge), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(module.name).assertIsEnabled().performClick()
        assertEquals(module.id, opened)
        compose.runOnIdle { installed.value = installed.value.copy(update = false) }
        compose.onNodeWithText(status, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun removalTakesPriorityOverPendingUpdate() {
        show {
            WearList { spec -> item { WearModuleItem(spec, module.copy(remove = true), {}) } }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.wear_pending_removal), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.reboot_to_apply), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun detailKeepsCheckedValueWhilePendingAndRestoresNormalSwitchAfterRefresh() {
        val installed = mutableStateOf(module)
        show { Detail(installed.value) }
        val enable = compose.activity.getString(R.string.wear_enable_module)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        compose.onNodeWithText(enable).assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.reboot_to_apply), useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { installed.value = installed.value.copy(remove = true) }
        compose.onNodeWithText(enable).assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.reboot_to_apply), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.wear_restore_module)).assertExists()
        compose.runOnIdle { installed.value = installed.value.copy(update = false, remove = false) }
        compose.onNodeWithText(enable).assertIsOn().assertIsEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.wear_enabled), useUnmergedTree = true).assertExists()
        compose.runOnIdle { installed.value = installed.value.copy(enabled = false) }
        compose.onNodeWithText(enable).assertIsOff().assertIsEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.wear_disabled), useUnmergedTree = true).assertExists()
    }

    @Test
    fun onlineUpdateWithoutLocalUpdateDoesNotRequireReboot() {
        show {
            Detail(module.copy(update = false, moduleUpdate = ModuleUpdateMetadata("https://example.com/module.zip", "2", "", 2)))
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        compose.onNodeWithText(compose.activity.getString(R.string.wear_enable_module)).assertIsOn().assertIsEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.reboot_to_apply), useUnmergedTree = true).assertDoesNotExist()
    }

    @Composable
    private fun Detail(installed: InstalledModule) {
        WearModuleDetail(installed, null, {}, { _, _ -> }, { _, _ -> }, {}, {}, {})
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
                fontScale = 1.15f
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    AppScaffold(timeText = {}) { Box(Modifier.requiredSize(216.dp)) { content() } }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}")
        fun screens() = listOf(false, true)
    }
}
