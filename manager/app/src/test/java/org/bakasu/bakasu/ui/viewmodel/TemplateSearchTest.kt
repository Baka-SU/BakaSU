package org.bakasu.bakasu.ui.viewmodel

import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateSearchTest {
    private val templates = listOf(
        ProfileTemplate(id = "app.Reader", name = "阅读器", description = "Read books", author = "Alice"),
        ProfileTemplate(id = "legacy", name = "Legacy", description = "旧模板", author = "Bob", local = false),
    )

    @Test
    fun matchesAllSupportedFieldsIgnoringCaseAndOuterWhitespace() {
        for (query in listOf(" reader ", "阅读", "BOOKS", "ALICE")) {
            assertEquals(listOf(templates[0]), filterProfileTemplates(templates, query))
        }
        assertEquals(listOf(templates[1]), filterProfileTemplates(templates, "旧模板"))
        assertEquals(emptyList<ProfileTemplate>(), filterProfileTemplates(templates, "missing"))
        assertEquals(templates, filterProfileTemplates(templates, "  "))
    }

    @Test
    fun filteringKeepsTheCompleteSelectionListAndReflectsDeletion() {
        val state = TemplateUiState(templateList = templates, profileTemplates = templates.map { it.id })
        assertEquals(listOf(templates[1]), filterProfileTemplates(state.templateList, "legacy"))
        assertEquals(listOf("app.Reader", "legacy"), state.profileTemplates)
        assertEquals(emptyList<ProfileTemplate>(), filterProfileTemplates(templates.dropLast(1), "legacy"))
    }
}
