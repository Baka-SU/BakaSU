package org.bakasu.bakasu.ui.wear

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.runBlocking
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.packageinfo.SuperUserRepository
import org.bakasu.bakasu.domain.model.SuperUserLoadFailure
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

class WearStartupRootTest {
    @get:Rule val compose = createAndroidComposeRule<WearMainActivity>()

    @Test
    fun unrootedStartupRecreationAndRetryDoNotShowTheAutomaticDialog() {
        val repository = GlobalContext.get().get<SuperUserRepository>()
        compose.waitUntil(30_000) { repository.state.value.loadFailure == SuperUserLoadFailure.ROOT_ACCESS_REQUIRED }
        fun check() {
            compose.onNodeWithText(compose.activity.getString(R.string.grant_root_failed)).assertDoesNotExist()
        }
        check()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30_000) { repository.state.value.loadFailure == SuperUserLoadFailure.ROOT_ACCESS_REQUIRED }
        check()
        runBlocking { repository.refresh() }
        check()
    }
}
