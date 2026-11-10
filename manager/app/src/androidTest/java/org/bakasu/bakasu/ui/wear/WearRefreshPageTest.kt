package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.InstalledApp
import org.bakasu.bakasu.domain.model.InstalledAppGroup
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.domain.model.SulogEntry
import org.bakasu.bakasu.domain.model.SulogEventType
import org.bakasu.bakasu.ui.viewmodel.ModuleUiState
import org.bakasu.bakasu.ui.viewmodel.SulogLoading
import org.bakasu.bakasu.ui.viewmodel.SulogUiState
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiState
import org.bakasu.bakasu.ui.wear.component.LocalWearTimeOptions
import org.bakasu.bakasu.ui.wear.component.WearChip
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearOperationsHost
import org.bakasu.bakasu.ui.wear.component.WearTimeOptions
import org.bakasu.bakasu.ui.wear.component.WearTimeText
import org.bakasu.bakasu.ui.wear.component.WearTimeTitleLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearRefreshPageTest(private val round: Boolean, private val mode: String, private val page: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun realPageMenuRefreshHidesTheIndicatorAfterScrollingAwayFromTheTop() {
        var loading by mutableStateOf(SulogLoading.NONE)
        var menu by mutableStateOf(false)
        val records = (1..100).map { SulogEntry("$it", SulogEventType.RootExecve, "seq=$it comm=Record-$it", null, mapOf("comm" to "Record-$it")) }
        val state = SulogUiState(isLoading = false, isSulogEnabled = true, sulogStatus = "supported", visibleEntries = records)
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
                screenWidthDp = 192
                screenHeightDp = 192
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalWearTimeOptions provides WearTimeOptions(mode)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp).testTag("viewport")) {
                        WearTimeTitleLayout(R.string.sulog) {
                            AppScaffold(timeText = { WearTimeText(compose.activity.getString(R.string.sulog)) }) {
                                WearOperationsHost("logs", menu, menu, { closing ->
                                    WearList(onClosePanel = { menu = false }, onPanelClosing = closing, closePanelRequested = loading == SulogLoading.REFRESH) { spec ->
                                        item { WearChip(spec, compose.activity.getString(R.string.wear_refresh), onClick = { loading = SulogLoading.REFRESH }) }
                                    }
                                }) {
                                    when (page) {
                                        "superuser" -> WearSuperUserPage(
                                            SuperUserUiState(
                                                appGroupList = (1..30).map { i ->
                                                    val app = InstalledApp("test.$i", "App $i", 10000 + i)
                                                    InstalledAppGroup(app.uid, app.packageName, listOf(app))
                                                },
                                                isRefreshing = loading == SulogLoading.REFRESH,
                                            ),
                                            null,
                                            {},
                                            { _, _ -> },
                                            {},
                                            { menu = true },
                                            hasOperationsPanel = true,
                                        )

                                        "modules" -> WearModulesPage(
                                            ModuleUiState(
                                                moduleList = (1..30).map { i ->
                                                    InstalledModule("$i", "Module $i", "Author", "v1", 1, "Description", true, false, false, "", false, false, false, null, null, "$i", null)
                                                },
                                                isRefreshing = loading == SulogLoading.REFRESH,
                                            ),
                                            null,
                                            {},
                                            {},
                                            { menu = true },
                                            hasOperationsPanel = true,
                                        )

                                        else -> WearLogsPage(state, {}, {}, { loading = SulogLoading.REFRESH }, { menu = true }, {}, hasOperationsPanel = true, loading = loading)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { menu = true }
        compose.onNodeWithText(compose.activity.getString(R.string.wear_refresh)).performClick()
        val indicator = compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading))
        indicator.assertIsDisplayed()
        compose.onNodeWithTag("viewport").performTouchInput {
            swipe(Offset(centerX, height * 0.75f), Offset(centerX, height * 0.25f), 500)
        }
        indicator.assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(if (page == "logs") 100 else 30)
        indicator.assertDoesNotExist()
        compose.runOnIdle { loading = SulogLoading.NONE }
        indicator.assertDoesNotExist()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, mode={1}, page={2}")
        fun configurations() = listOf(true, false).flatMap { round -> listOf("time", "title", "hidden").flatMap { mode -> listOf("superuser", "modules", "logs").map { arrayOf<Any>(round, mode, it) } } }
    }
}
