package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearModuleUpdateTest(private val round: Boolean) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val update = ModuleUpdateMetadata("https://example.com/module.zip", "2", "https://example.com/log", 2)
    private val module = InstalledModule(
        id = "module", name = "Module", author = "Author", version = "1", versionCode = 1,
        description = "", enabled = true, update = false, remove = false,
        updateJson = "https://example.com/update.json", hasWebUi = false, hasActionScript = false,
        metamodule = false, actionIconPath = null, webUiIconPath = null, dirId = "module", moduleUpdate = update,
    )
    private var openedChangelog = false
    private var downloaded = false

    @Test
    fun downloadIsAvailableBeforeOpeningChangelog() {
        showPage(module)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithText(compose.activity.getString(R.string.wear_update_download)).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertTrue(downloaded)
            assertFalse(openedChangelog)
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.onNodeWithText(compose.activity.getString(R.string.module_changelog)).performClick()
        compose.runOnIdle { assertTrue(openedChangelog) }
    }

    @Test
    fun missingChangelogDoesNotBlockDownload() {
        showPage(module.copy(moduleUpdate = update.copy(changelog = "")))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.onNodeWithText(compose.activity.getString(R.string.module_changelog)).assertIsNotEnabled()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithText(compose.activity.getString(R.string.wear_update_download)).assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(downloaded) }
    }

    @Test
    fun longModuleNameAndVersionRemainUnclipped() {
        val installed = module.copy(name = "A module with a deliberately long name", moduleUpdate = update.copy(version = "2026.11.08-long-release-version"))
        showPage(installed)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        val version = compose.activity.getString(R.string.wear_update_version, installed.version, installed.moduleUpdate!!.version)
        for (text in listOf(installed.name, version)) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse(layouts.single().hasVisualOverflow)
        }
    }

    private fun showPage(installed: InstalledModule) {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        Box(Modifier.size(192.dp)) {
                            WearModuleUpdatePage(
                                installed,
                                onBack = {},
                                onDownload = { selected, metadata ->
                                    assertEquals(installed, selected)
                                    assertEquals(installed.moduleUpdate, metadata)
                                    downloaded = true
                                },
                                onChangelog = { openedChangelog = true },
                            )
                        }
                    }
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
