package org.bakasu.bakasu.ui.viewmodel

import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.DynamicManagerApp
import org.junit.Assert.assertEquals
import org.junit.Test

class ManagerSourceTest {
    @Test
    fun fixedSourcesShareResourceLabelsAndDynamicManagersRemainChangeable() {
        for ((index, label) in listOf(253 to R.string.dynamic_manager_source_toolkit, 254 to R.string.dynamic_manager_source_debug, 0 to R.string.dynamic_manager_source_kernel, 252 to R.string.dynamic_manager_source_kernel, 255 to null, null to null)) {
            val app = DynamicManagerApp("Manager", "test.manager", 110001, "/app/base.apk", managerSignatureIndex = index, isChangeable = index == null || index == 255)
            assertEquals(label, app.managerSourceLabel())
        }
    }
}
