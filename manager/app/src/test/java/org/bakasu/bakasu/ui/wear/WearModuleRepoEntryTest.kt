package org.bakasu.bakasu.ui.wear

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class WearModuleRepoEntryTest {
    @Test
    fun repositoryOpensImmediatelyAndRepeatedTapsDoNotDuplicateTheDestination() {
        val navigator = WearNavigator(NavBackStack<NavKey>(WearRoute.Main), {}, {}, {})
        val menu = WearRoute.Operations(WearOperationsPage.Modules)
        navigator.open(menu)
        repeat(3) { navigator.open(WearRoute.ModuleRepo) }
        assertEquals(listOf(WearRoute.Main, menu, WearRoute.ModuleRepo), navigator.backStack.toList())
        navigator.back()
        assertEquals(menu, navigator.top)
    }
}
