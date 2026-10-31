package org.bakasu.bakasu.ui.wear

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class WearNavigatorTest {
    private fun navigator() = WearNavigator(NavBackStack<NavKey>(WearRoute.Main), {}, {}, { fail("Unexpected app exit") })

    @Test
    fun openingANestedPageKeepsOnlyItsOwnersMenu() {
        val navigator = navigator()
        val modules = WearRoute.Operations(WearOperationsPage.Modules)
        val repository = WearRoute.Operations(WearOperationsPage.Repository)
        navigator.open(modules)
        navigator.open(WearRoute.ModuleRepo)
        assertEquals(modules, navigator.operationsFor(WearRoute.Main))
        assertNull(navigator.operationsFor(WearRoute.ModuleRepo))
        navigator.open(repository)
        navigator.open(WearRoute.RepositorySources)
        assertEquals(repository, navigator.operationsFor(WearRoute.ModuleRepo))
        assertNull(navigator.operationsFor(WearRoute.RepositorySources))
        navigator.back()
        assertEquals(repository, navigator.top)
        navigator.back()
        assertNull(navigator.operationsFor(WearRoute.ModuleRepo))
        assertEquals(modules, navigator.operationsFor(WearRoute.Main))
    }

    @Test
    fun backFromAnActionReturnsToItsOperationsMenu() {
        val actions = mapOf(
            WearOperationsPage.Home to listOf(WearRoute.Logs, WearRoute.SuSFS, WearRoute.Reboot),
            WearOperationsPage.SuperUser to listOf(WearRoute.Logs, WearRoute.AllowlistBackup, WearRoute.AppFilter),
            WearOperationsPage.Modules to listOf(WearRoute.ModuleRepo, WearRoute.ModuleSort),
            WearOperationsPage.Logs to listOf(WearRoute.LogOptions),
            WearOperationsPage.Repository to listOf(WearRoute.ModuleRepoSort, WearRoute.Downloads, WearRoute.RepositorySources),
        )
        actions.forEach { (page, routes) ->
            routes.forEach { route ->
                val navigator = navigator()
                val menu = WearRoute.Operations(page)
                navigator.open(menu)
                navigator.open(route)
                navigator.back()
                assertEquals(menu, navigator.top)
            }
        }
    }

    @Test
    fun searchResultsReturnToTheNestedRepositoryMenu() {
        val navigator = navigator()
        navigator.open(WearRoute.Operations(WearOperationsPage.Modules))
        navigator.open(WearRoute.ModuleRepo)
        val menu = WearRoute.Operations(WearOperationsPage.Repository)
        navigator.open(menu)
        navigator.open(WearRoute.ModuleRepoSearch)
        var query = ""
        navigator.submitSearch(WearRoute.ModuleRepoSearchResults, " module ") { query = it }
        assertEquals("module", query)
        navigator.back()
        assertEquals(menu, navigator.top)
        navigator.back()
        assertEquals(WearRoute.ModuleRepo, navigator.top)
        navigator.back()
        assertEquals(WearRoute.Operations(WearOperationsPage.Modules), navigator.top)
    }
}
