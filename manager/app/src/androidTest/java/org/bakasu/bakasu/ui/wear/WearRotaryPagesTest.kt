package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import java.util.Locale
import org.bakasu.bakasu.domain.model.HomeDashboardState
import org.bakasu.bakasu.domain.model.InstalledApp
import org.bakasu.bakasu.domain.model.InstalledAppGroup
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.ui.viewmodel.ModuleUiState
import org.bakasu.bakasu.ui.viewmodel.SettingsUiState
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiState
import org.bakasu.bakasu.ui.wear.component.WearOperationsHost
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearRotaryPagesTest(private val page: String, private val round: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun continuousRotationScrollsTheReportedPageAndItsControls() {
        val revision = mutableIntStateOf(0)
        val home = HomeDashboardState(isInitialDataLoaded = true, isCoreDataLoaded = true, isExtendedDataLoaded = true)
        val apps = (1..12).map {
            val app = InstalledApp("test.app$it", "App $it", 10000 + it)
            InstalledAppGroup(app.uid, app.packageName, listOf(app))
        }
        val modules = (1..12).map { InstalledModule("module$it", "Module $it", "Author", "1", 1, "", true, false, false, "", false, false, false, null, null, "module$it", null) }
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            val generation = revision.intValue
            val onBack: () -> Unit = { check(generation >= 0) }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        when (page) {
                            "home" -> WearHomePage(home, null, onBack, {})

                            "superuser-menu", "modules-menu" -> WearOperationsHost(
                                menuKey = page,
                                menuOpen = true,
                                menuActive = true,
                                menuContent = { closing ->
                                    WearOperationsPanel(
                                        if (page == "superuser-menu") WearOperationsPage.SuperUser else WearOperationsPage.Modules,
                                        onBack, {}, {}, {}, true, true, true, {}, onClosing = closing,
                                    )
                                },
                            ) {}

                            "theme-color" -> WearColorPage(SettingsUiState(), onBack, {}, {}, {})

                            "superuser" -> WearSuperUserPage(SuperUserUiState(appGroupList = apps, isRefreshing = false), null, {}, { _, _ -> }, onBack, {})

                            "modules" -> WearModulesPage(ModuleUiState(moduleList = modules), null, {}, { check(generation >= 0) }, {})

                            "settings" -> WearSettingsPage(SettingsUiState(), home.systemStatus, {}, {}, onBack)

                            "theme-language-choice" -> WearLanguagePage(Locale.US, listOf(Locale.US), onBack, {})
                        }
                    }
                }
            }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToIndex(0)
        val range = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        val before = range.value()
        val expected = minOf(80f, (range.maxValue() - before) / 2f)
        assertTrue("$page must have scrollable content", expected > 0f)
        compose.mainClock.autoAdvance = false
        try {
            repeat(30) {
                compose.runOnUiThread {
                    val now = SystemClock.uptimeMillis()
                    val coordinates = MotionEvent.PointerCoords().apply {
                        setAxisValue(MotionEvent.AXIS_SCROLL, -8f * compose.activity.resources.displayMetrics.density / ViewConfiguration.get(compose.activity).scaledVerticalScrollFactor)
                    }
                    val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(MotionEvent.PointerProperties().apply { id = 0 }), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_ROTARY_ENCODER, 0)
                    try {
                        compose.activity.dispatchGenericMotionEvent(event)
                    } finally {
                        event.recycle()
                    }
                    revision.intValue++
                }
                compose.mainClock.advanceTimeBy(16)
            }
            compose.mainClock.advanceTimeBy(1500)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        assertTrue("$page should scroll continuously: before=$before, after=${range.value()}", range.value() - before >= expected)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "page={0}, round={1}")
        fun pages() = listOf("home", "superuser-menu", "modules-menu", "theme-color", "superuser", "modules", "settings", "theme-language-choice").flatMap { page -> listOf(false, true).map { arrayOf<Any>(page, it) } }
    }
}
