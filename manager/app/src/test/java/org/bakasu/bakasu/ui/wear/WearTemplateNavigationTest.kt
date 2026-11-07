package org.bakasu.bakasu.ui.wear

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class WearTemplateNavigationTest {
    @Test
    fun cloudDetailsUseAnIndependentEntryAndKeepTheirParentUntilReturning() {
        val navigator = WearNavigator(NavBackStack<NavKey>(WearRoute.Main, WearRoute.OnlineTemplates), {}, {}, {})
        val detail = WearRoute.OnlineTemplateDetail("cloud")
        navigator.open(detail)
        assertEquals(listOf(WearRoute.Main, WearRoute.OnlineTemplates, detail), navigator.backStack)
        navigator.back()
        assertEquals(WearRoute.OnlineTemplates, navigator.top)
        navigator.open(detail)
        navigator.closeUnavailable(fullFeatured = false, susfs = false, rootAvailable = false)
        assertEquals(listOf(WearRoute.Main), navigator.backStack)
    }

    @Test
    fun reSearchFromMenuUpdatesExistingResultsAndDetailsKeepQuery() {
        var query = ""
        val navigator = WearNavigator(
            NavBackStack<NavKey>(WearRoute.Main, WearRoute.Templates),
            {},
            { if (it == WearRoute.TemplateSearchResults) query = "" },
            { fail("Unexpected exit") },
        )
        val menu = WearRoute.Operations(WearOperationsPage.Templates)
        navigator.open(menu)
        navigator.open(WearRoute.TemplateSearch)
        navigator.submitTemplateSearch(" Legacy ") { query = it }
        assertEquals("Legacy", query)
        navigator.open(WearRoute.TemplateEditor("legacy", true, false))
        navigator.back()
        assertEquals("Legacy", query)
        navigator.open(menu)
        navigator.open(WearRoute.TemplateSearch)
        navigator.submitTemplateSearch(" Alice ") { query = it }
        assertEquals("Alice", query)
        assertEquals(1, navigator.backStack.count { it == WearRoute.TemplateSearchResults })
        navigator.back()
        assertEquals("", query)
        assertEquals(menu, navigator.top)
        navigator.back()
        assertEquals(WearRoute.Templates, navigator.top)
    }

    @Test
    fun blankSearchKeepsTheResultsMenuAndQuery() {
        val menu = WearRoute.Operations(WearOperationsPage.Templates)
        val navigator = WearNavigator(NavBackStack<NavKey>(WearRoute.Main, WearRoute.TemplateSearchResults, menu, WearRoute.TemplateSearch), {}, {}, {})
        navigator.submitTemplateSearch(" ") { fail("Blank search dispatched") }
        assertEquals(menu, navigator.top)
        assertEquals(1, navigator.backStack.count { it == WearRoute.TemplateSearchResults })
    }
}
