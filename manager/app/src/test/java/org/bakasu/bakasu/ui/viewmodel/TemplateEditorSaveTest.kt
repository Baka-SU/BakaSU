package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.bakasu.bakasu.domain.usecase.SaveProfileTemplateUseCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateEditorSaveTest {
    @Test
    fun explicitSaveFlushesDebouncedChangesAndFailuresKeepTheDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val initial = ProfileTemplate("local", "Original")
        val edited = initial.copy(name = "Edited")
        val save = mockk<SaveProfileTemplateUseCase>()
        coEvery { save(any(), false) } returns Result.failure(IllegalStateException("storage"))
        val model = TemplateEditorViewModel("local", false, false, mockk { coEvery { this@mockk.invoke("local") } returns Result.success(initial) }, save, mockk())
        store.put("editor", model)
        val events = mutableListOf<TemplateEditorUiEvent>()
        backgroundScope.launch { model.events.collect { events += it } }
        try {
            runCurrent()
            model.dispatch(TemplateEditorUiAction.Update(edited, autoSave = true))
            model.dispatch(TemplateEditorUiAction.Save)
            runCurrent()
            advanceTimeBy(500)
            runCurrent()
            coVerify(exactly = 1) { save(edited, false) }
            assertTrue(model.state.value.dirty)
            assertTrue(events.single() is TemplateEditorUiEvent.Error)
            coEvery { save(any(), false) } returns Result.success(Unit)
            model.dispatch(TemplateEditorUiAction.Save)
            runCurrent()
            assertFalse(model.state.value.dirty)
            assertFalse(model.state.value.saving)
            assertTrue(events.last() is TemplateEditorUiEvent.Saved)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun newDraftDoesNotAutoSaveAndExistingEditsDo() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val save = mockk<SaveProfileTemplateUseCase> { coEvery { this@mockk.invoke(any(), any()) } returns Result.success(Unit) }
        val existing = ProfileTemplate("local", "Original")
        val creation = TemplateEditorViewModel("new", false, true, mockk(), save, mockk())
        val editor = TemplateEditorViewModel("local", false, false, mockk { coEvery { this@mockk.invoke("local") } returns Result.success(existing) }, save, mockk())
        store.put("new", creation)
        store.put("existing", editor)
        try {
            runCurrent()
            creation.dispatch(TemplateEditorUiAction.Update(ProfileTemplate("new", "Draft"), autoSave = true))
            editor.dispatch(TemplateEditorUiAction.Update(existing.copy(name = "Updated"), autoSave = true))
            runCurrent()
            advanceTimeBy(301)
            runCurrent()
            coVerify(exactly = 0) { save(match { it.id == "new" }, any()) }
            coVerify(exactly = 1) { save(existing.copy(name = "Updated"), false) }
            assertFalse(editor.state.value.dirty)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
