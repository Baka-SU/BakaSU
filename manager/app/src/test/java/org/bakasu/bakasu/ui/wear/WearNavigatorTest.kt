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
    fun patchingWithoutRootReturnsToThePageBeforeInstallation() {
        val navigator = navigator()
        navigator.open(WearRoute.KernelInstall)
        navigator.replace(WearRoute.LkmInstall)
        navigator.back()
        assertEquals(listOf(WearRoute.Main), navigator.backStack.toList())
    }

    @Test
    fun rootedInstallationKeepsTheModeChooserWhenReturningFromPatching() {
        val navigator = navigator()
        navigator.open(WearRoute.KernelInstall)
        navigator.open(WearRoute.LkmInstall)
        navigator.back()
        assertEquals(WearRoute.KernelInstall, navigator.top)
        navigator.open(WearRoute.Ak3Install())
        assertEquals(WearRoute.Ak3Install(), navigator.top)
    }

    @Test
    fun changelogReturnsToTheModuleUpdatePage() {
        val navigator = navigator()
        navigator.open(WearRoute.Module("module"))
        navigator.open(WearRoute.ModuleUpdate("module"))
        navigator.open(WearRoute.ModuleChangelog("module"))
        navigator.back()
        assertEquals(WearRoute.ModuleUpdate("module"), navigator.top)
        navigator.back()
        assertEquals(WearRoute.Module("module"), navigator.top)
    }

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
            WearOperationsPage.Home to listOf(WearRoute.SuSFS, WearRoute.Reboot),
            WearOperationsPage.SuperUser to listOf(WearRoute.Logs, WearRoute.AllowlistBackup, WearRoute.AppFilter),
            WearOperationsPage.Modules to listOf(WearRoute.ModuleRepo, WearRoute.ModuleSort),
            WearOperationsPage.Logs to listOf(WearRoute.LogOptions),
            WearOperationsPage.Umount to listOf(WearRoute.AddUmount, WearRoute.UmountSearch),
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

    @Test
    fun logSearchShowsResultsAndKeepsQueryThroughDetailsAndEditing() {
        var query = ""
        val navigator = WearNavigator(
            NavBackStack<NavKey>(WearRoute.Main),
            {},
            { if (it == WearRoute.LogSearchResults) query = "" },
            { fail("Unexpected app exit") },
        )
        val menu = WearRoute.Operations(WearOperationsPage.Logs)
        navigator.open(WearRoute.Logs)
        navigator.open(menu)
        navigator.open(WearRoute.LogSearch)
        navigator.submitSearch(WearRoute.LogSearchResults, " root ") { query = it }
        assertEquals(WearRoute.LogSearchResults, navigator.top)
        assertEquals("root", query)
        assertNull(navigator.operationsFor(WearRoute.LogSearchResults))
        navigator.open(WearRoute.LogEntry("entry"))
        navigator.back()
        assertEquals("root", query)
        navigator.open(WearRoute.LogSearch)
        navigator.submitSearch(WearRoute.LogSearchResults, " shell ") { query = it }
        assertEquals(1, navigator.backStack.count { it == WearRoute.LogSearchResults })
        assertEquals("shell", query)
        navigator.back()
        assertEquals("", query)
        assertEquals(menu, navigator.top)
        navigator.back()
        assertEquals(WearRoute.Logs, navigator.top)
    }

    @Test
    fun blankLogSearchClosesInputWithoutOpeningResults() {
        val navigator = navigator()
        navigator.open(WearRoute.Logs)
        navigator.open(WearRoute.LogSearch)
        navigator.submitSearch(WearRoute.LogSearchResults, " ") { fail("Blank query dispatched") }
        assertEquals(WearRoute.Logs, navigator.top)
    }

    @Test
    fun dynamicManagerInputsReturnToTheirOwningPageAndCloseWhenRootIsLost() {
        val navigator = navigator()
        navigator.open(WearRoute.DynamicManager)
        navigator.open(WearRoute.DynamicManagerManual)
        for (input in listOf(WearRoute.DynamicManagerSize, WearRoute.DynamicManagerHash)) {
            navigator.open(input)
            navigator.back()
            assertEquals(WearRoute.DynamicManagerManual, navigator.top)
        }
        navigator.back()
        navigator.open(WearRoute.DynamicManagerApps)
        navigator.open(WearRoute.DynamicManagerSearch)
        navigator.back()
        assertEquals(WearRoute.DynamicManagerApps, navigator.top)
        navigator.closeUnavailable(fullFeatured = false, susfs = false, rootAvailable = false)
        assertEquals(listOf(WearRoute.Main), navigator.backStack.toList())
    }
}
