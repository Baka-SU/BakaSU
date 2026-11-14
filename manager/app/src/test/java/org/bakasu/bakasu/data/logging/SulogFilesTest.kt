package org.bakasu.bakasu.data.logging

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SulogFilesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun log(name: String, content: String = "event\n"): File = temporaryFolder.newFile(name).apply { writeText(content) }

    @Test
    fun emptyActiveFileIsHiddenWithoutChangingTheCleaningTarget() {
        val previous = log("sulog-2026-11-05.log")
        val active = log("sulog-2026-11-06.log", "")
        val visible = listSulogFiles(temporaryFolder.root, includeEmpty = false)
        assertEquals(listOf(previous.path), visible.map { it.path })
        assertEquals(active.path, listSulogFiles(temporaryFolder.root, includeEmpty = true).first().path)
        assertEquals(previous.path, visible.selectedSulogFile(active.path)?.path)
    }

    @Test
    fun clearingLastFileRemovesSelectionAndNewContentMakesItVisibleAgain() {
        val active = log("sulog-2026-11-06.log")
        active.writeText("")
        assertNull(listSulogFiles(temporaryFolder.root, includeEmpty = false).selectedSulogFile(active.path))
        active.appendText("new event\n")
        assertEquals(active.path, listSulogFiles(temporaryFolder.root, includeEmpty = false).selectedSulogFile(active.path)?.path)
    }

    @Test
    fun deletingOneRotationPreservesOtherFilesAndSelectsNewestRemainingContent() {
        val oldest = log("sulog-2026-11-05.log")
        val rotated = log("sulog-2026-11-06-2.log")
        val newest = log("sulog-2026-11-06-10.log")
        log("unrelated.log")
        var visible = listSulogFiles(temporaryFolder.root, includeEmpty = false)
        assertEquals(listOf(newest.path, rotated.path, oldest.path), visible.map { it.path })
        assertEquals(oldest.path, visible.selectedSulogFile(oldest.path)?.path)
        rotated.delete()
        visible = listSulogFiles(temporaryFolder.root, includeEmpty = false)
        assertEquals(listOf(newest.path, oldest.path), visible.map { it.path })
        assertEquals(newest.path, visible.selectedSulogFile(rotated.path)?.path)
    }
}
