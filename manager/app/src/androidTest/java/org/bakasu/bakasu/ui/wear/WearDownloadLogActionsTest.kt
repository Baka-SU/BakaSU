package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import com.topjohnwu.superuser.io.SuFile
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.logging.SulogRepository
import org.bakasu.bakasu.domain.model.DownloadModuleInfo
import org.bakasu.bakasu.domain.model.DownloadState
import org.bakasu.bakasu.domain.model.DownloadStatus
import org.bakasu.bakasu.ui.viewmodel.SulogUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.koin.core.context.GlobalContext

@RunWith(Parameterized::class)
class WearDownloadLogActionsTest(private val round: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0},font={1}")
        fun parameters() = listOf(arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1.3f), arrayOf<Any>(false, 1.3f))
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingTwoDownloadsOpensTheirOwnModuleWithoutInstalling() {
        val first = DownloadState(1, "first.zip", "one", module = DownloadModuleInfo("First", moduleId = "first"))
        val second = DownloadState(2, "second.zip", "two", status = DownloadStatus.FAILED, module = DownloadModuleInfo("Second", moduleId = "second"))
        val opened = mutableListOf<String>()
        var installs = 0
        show { WearDownloadsPage(listOf(first, second), false, {}, { installs++ }, {}, {}, { opened += it.module?.moduleId.orEmpty() }) }
        scrollTo("First")
        compose.onNodeWithText("First").performClick()
        scrollTo("Second")
        compose.onNodeWithText("Second").performClick()
        assertEquals(listOf("first", "second"), opened)
        assertEquals(0, installs)
    }

    @Test
    fun downloadsUseOneRowRevealBeforeRemovalAndRestoreTheBackEdge() {
        val longName = "Very long module name 模块名称需要单行省略而且不能遮挡进度"
        val tasks = mutableStateOf(
            listOf(
                DownloadState(1, "active.zip", "one", progress = 37, totalBytes = 100, module = DownloadModuleInfo(longName)),
                DownloadState(2, "unknown.zip", "two", module = DownloadModuleInfo("Unknown size", "https://example.invalid/missing.png")),
                DownloadState(3, "done.zip", "three", status = DownloadStatus.COMPLETED, resultUri = "file:///done.zip"),
            ),
        )
        var installs = 0
        var backs = 0
        val removed = mutableListOf<Int>()
        show {
            WearDownloadsPage(tasks.value, false, { backs++ }, { installs++ }, { id ->
                removed += id
                tasks.value = tasks.value.filterNot { it.id == id }
            }, { tasks.value = emptyList() })
        }
        scrollTo(longName)
        compose.onNodeWithText("37%").assertExists()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(longName, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        compose.onNodeWithText(longName).performTouchInput { swipeLeft() }
        compose.runOnIdle { assertTrue(removed.isEmpty()) }
        compose.onNodeWithContentDescription(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.delete)).assertIsDisplayed().performClick()
        save("download-remove-confirm")
        dismiss()
        compose.runOnIdle { assertTrue(removed.isEmpty()) }
        scrollTo(longName)
        compose.onNodeWithText(longName).performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.delete)).performClick()
        confirm()
        compose.runOnIdle { assertEquals(listOf(1), removed) }
        scrollTo("Unknown size")
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.wear_downloading)).assertExists()
        compose.onNodeWithText("0%").assertDoesNotExist()
        save("downloads-unknown-size")
        scrollTo("done.zip")
        compose.onNodeWithText("done.zip").performClick()
        compose.runOnIdle { assertEquals(1, installs) }
        repeat(3) { compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() } }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.wear_download_clear_all)).assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(InstrumentationRegistry.getInstrumentation().targetContext.getString(androidx.wear.compose.material3.R.string.wear_m3c_alert_dialog_content_description_confirm_button)).fetchSemanticsNodes().isNotEmpty() }
        confirm()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.wear_downloads_empty)).assertExists()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.wear_download_clear_all)).assertDoesNotExist()
        save("downloads-empty")
        repeat(2) { compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() } }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.back)).performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test
    fun deletingADateTargetsItsPathAndTruncatingTheWriterAllowsNewRecords() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "sulog-actions-${System.nanoTime()}").apply { mkdirs() }
        val latest = File(directory, "sulog-2026-11-07.log").apply { writeText("seq=1 type=root_execve comm=old\n") }
        val history = File(directory, "sulog-2026-11-06.log").apply { writeText("seq=2 type=root_execve comm=history\n") }
        val repository = SulogRepository(GlobalContext.get().get(), SuFile(directory.path))
        runBlocking { repository.refresh(silent = true).getOrThrow() }
        val cleaned = mutableListOf<String>()
        FileOutputStream(latest, true).use { writer ->
            show {
                val source by repository.state.collectAsState()
                WearLogOptionsPage(
                    SulogUiState(isLoading = false, files = source.files, selectedFilePath = source.selectedFilePath),
                    {},
                    { path -> runBlocking { repository.selectFile(path).getOrThrow() } },
                    {},
                    { path ->
                        runBlocking { repository.clean(path).getOrThrow() }
                        cleaned += path
                    },
                )
            }
            scrollTo("2026-11-06")
            compose.onNodeWithText("2026-11-06").performTouchInput { swipeLeft() }
            compose.onNodeWithContentDescription(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.delete)).performClick()
            confirm()
            assertEquals(listOf(history.path), cleaned)
            assertFalse(history.exists())
            assertEquals(latest.path, repository.state.value.selectedFilePath)
            compose.onNodeWithText("2026-11-06").assertDoesNotExist()
            scrollTo("2026-11-07")
            compose.onNodeWithText("2026-11-07").performTouchInput { swipeLeft() }
            compose.onNodeWithContentDescription(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.delete)).performClick()
            confirm()
            assertTrue(latest.exists())
            assertEquals(0L, latest.length())
            assertTrue(repository.state.value.files.isEmpty())
            compose.onNodeWithText("2026-11-07").assertDoesNotExist()
            save("logs-empty-after-clear")
            writer.write("seq=3 type=root_execve comm=new\n".toByteArray())
            writer.flush()
            runBlocking { repository.refresh(silent = true).getOrThrow() }
            scrollTo("2026-11-07")
            compose.onNodeWithText("2026-11-07").assertIsDisplayed()
            assertEquals("3", repository.state.value.entries.single().key)
            assertFalse(repository.state.value.isRefreshing)
            assertFalse(repository.state.value.isLoading)
            save("logs-new-record-after-clear")
        }
        directory.deleteRecursively()
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme { AppScaffold(timeText = {}) { content() } }
            }
        }
    }

    private fun scrollTo(text: String) {
        if (!compose.onNodeWithText(text).isDisplayed()) {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        }
    }
    private fun confirm() {
        compose.onNodeWithContentDescription(InstrumentationRegistry.getInstrumentation().targetContext.getString(androidx.wear.compose.material3.R.string.wear_m3c_alert_dialog_content_description_confirm_button)).performClick()
    }
    private fun dismiss() {
        compose.onNodeWithContentDescription(InstrumentationRegistry.getInstrumentation().targetContext.getString(androidx.wear.compose.material3.R.string.wear_m3c_alert_dialog_content_description_dismiss_button)).performClick()
    }
    private fun save(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name-$round-$fontScale.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
