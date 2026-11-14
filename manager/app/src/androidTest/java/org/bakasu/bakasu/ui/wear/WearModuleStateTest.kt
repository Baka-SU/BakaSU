package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ButtonDefaults
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
    fun enabledModuleFallbackIconHasADistinctThemeContainer() {
        val installed = mutableStateOf(module.copy(name = "Module", update = false))
        var activeColor = Color.Unspecified
        var inactiveColor = Color.Unspecified
        show {
            activeColor = MaterialTheme.colorScheme.primary
            inactiveColor = MaterialTheme.colorScheme.primaryContainer
            WearList { spec -> item { WearModuleItem(spec, installed.value, {}) } }
        }
        fun tilePixel(): Int {
            val title = compose.onNodeWithText("Module", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val version = compose.onNodeWithText(module.version, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val density = compose.activity.resources.displayMetrics.density
            val x = ((title.left - ButtonDefaults.IconSpacing - 4.dp).value * density).toInt()
            val y = ((title.top.value + title.bottom.value + version.top.value + version.bottom.value) / 4 * density).toInt()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            if (installed.value.enabled) {
                java.io.File(compose.activity.getExternalFilesDir(null), "active-module-icon-$round.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            return bitmap.getPixel(x, y)
        }
        assertEquals("An active module's icon must stand out from its primaryContainer row", activeColor.toArgb(), tilePixel())
        assertFalse(activeColor == inactiveColor)
        compose.runOnIdle { installed.value = installed.value.copy(enabled = false) }
        assertEquals("Inactive modules retain the tonal fallback", inactiveColor.toArgb(), tilePixel())
    }

    @Test
    fun pendingUpdateKeepsMetaInTheTitleWithoutAStatusLabel() {
        val installed = mutableStateOf(module.copy(version = "version-".repeat(12), metamodule = true))
        var opened: String? = null
        show {
            WearList { spec -> item { WearModuleItem(spec, installed.value, { opened = it }) } }
        }
        val status = compose.activity.getString(R.string.reboot_to_apply)
        compose.onNodeWithText(status, useUnmergedTree = true).assertDoesNotExist()
        val titleBounds = compose.onNodeWithText(module.name, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val versionBounds = compose.onNodeWithText(installed.value.version, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val metaBounds = compose.onNodeWithText(compose.activity.getString(R.string.wear_meta_badge), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("META shares the title center", (titleBounds.top.value + titleBounds.bottom.value) / 2, (metaBounds.top.value + metaBounds.bottom.value) / 2, 0.5f)
        assertFalse("The version belongs below the title", titleBounds.bottom > versionBounds.top)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(compose.activity.getExternalFilesDir(null), "fixed-meta-list-$round.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText(module.name).assertIsEnabled().performClick()
        assertEquals(module.id, opened)
        compose.runOnIdle { installed.value = installed.value.copy(update = false) }
        compose.onNodeWithText(status, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun detailKeepsMetaBesideItsSingleLineTitle() {
        show { Detail(module.copy(version = "v1.2", metamodule = true)) }
        val title = compose.onNodeWithText(module.name, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val version = compose.onNodeWithText("v1.2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val badge = compose.onNodeWithText(compose.activity.getString(R.string.wear_meta_badge), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((title.top.value + title.bottom.value) / 2, (badge.top.value + badge.bottom.value) / 2, 0.5f)
        assertFalse(title.bottom > version.top)
        listOf(module.name, "v1.2").forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
        }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(compose.activity.getExternalFilesDir(null), "fixed-meta-detail-$round.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun pendingRemovalAndUpdateHaveNoStatusLabels() {
        show {
            WearList { spec -> item { WearModuleItem(spec, module.copy(remove = true), {}) } }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.wear_pending_removal), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.reboot_to_apply), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun detailKeepsCheckedValueWhilePendingAndRestoresNormalSwitchAfterRefresh() {
        val installed = mutableStateOf(module)
        show { Detail(installed.value) }
        val enable = compose.activity.getString(R.string.wear_enable_module)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        compose.onNodeWithText(enable).assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.reboot_to_apply), useUnmergedTree = true).assertDoesNotExist()
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

    @Test
    fun removalIsAbsentFromTheSummaryButStillRestorable() {
        show { Detail(module.copy(remove = true)) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText(compose.activity.getString(R.string.wear_pending_removal), useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        compose.onNodeWithText(compose.activity.getString(R.string.wear_enable_module)).assertIsNotEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.wear_restore_module)).assertExists()
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
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(LocalDensity.current.density, 1.15f)) {
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
