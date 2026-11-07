package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.AppProfile
import org.bakasu.bakasu.domain.model.InstalledApp
import org.bakasu.bakasu.domain.model.InstalledAppGroup
import org.bakasu.bakasu.ui.viewmodel.AppProfileUiAction
import org.bakasu.bakasu.ui.viewmodel.AppProfileUiEvent
import org.bakasu.bakasu.ui.viewmodel.AppProfileUiState
import org.bakasu.bakasu.ui.viewmodel.TemplateUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Exercises the production route content and resume effects with separate profile state per page. */
@RunWith(Parameterized::class)
class WearAppProfileNavigationTest(private val round: Boolean, private val defaultUmount: Boolean) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = InstalledApp("test.app", "Test application", 10000)
    private val group = InstalledAppGroup(app.uid, app.packageName, listOf(app))
    private val appRoute = WearRoute.App(app.uid, app.packageName)
    private val modeRoute = WearRoute.AppProfileMode(app.uid, app.packageName)
    private val customRoute = WearRoute.AppProfileCustom(app.uid, app.packageName)
    private val stack = NavBackStack<NavKey>(WearRoute.Main, appRoute)
    private val navigator = WearNavigator(stack, {}, {}, {})
    private var stored = AppProfile(app.packageName, currentUid = app.uid, umountModules = !defaultUmount)
    private var globalUmount = defaultUmount
    private var saves = 0
    private var detailLoads = 0
    private lateinit var detail: PageState
    private lateinit var mode: PageState
    private lateinit var custom: PageState

    @Test
    fun customSelectionReturnsToDetailsAndReloadsSavedProfile() {
        show()
        openMode()
        choose(R.string.profile_custom, 2)
        assertEquals(modeRoute, navigator.top)
        assertTrue(stored.nonRootUseDefault)
        completeModeSave()
        assertEquals(appRoute, navigator.top)
        assertEquals(2, stack.size)
        assertTrue(detailLoads > 0)
        assertFalse(stored.nonRootUseDefault)
        assertEquals(!defaultUmount, stored.umountModules)
        assertUmount(!defaultUmount)

        compose.onNodeWithText(text(R.string.profile_umount_modules)).performClick()
        assertUmount(defaultUmount)
        compose.onNodeWithText(text(R.string.profile_umount_modules)).performClick()
        assertUmount(!defaultUmount)
        compose.runOnIdle { globalUmount = !defaultUmount }
        openMode()
        choose(R.string.profile_custom, 2)
        assertEquals(appRoute, navigator.top)
        assertUmount(!defaultUmount)
        compose.runOnIdle { navigator.back() }
        compose.waitForIdle()
        compose.runOnIdle { navigator.open(appRoute) }
        compose.waitForIdle()
        assertUmount(!defaultUmount)
    }

    @Test
    fun choosingExistingCustomReturnsWithoutSaving() {
        stored = stored.copy(nonRootUseDefault = false)
        show()
        openMode()
        choose(R.string.profile_custom, 2)
        assertEquals(appRoute, navigator.top)
        assertEquals(0, saves)
        assertUmount(!defaultUmount)
    }

    @Test
    fun choosingDefaultWaitsForSaveAndKeepsIndividualValue() {
        stored = stored.copy(nonRootUseDefault = false)
        show()
        openMode()
        choose(R.string.profile_default, 1)
        assertEquals(modeRoute, navigator.top)
        assertFalse(stored.nonRootUseDefault)
        completeModeSave()
        assertEquals(appRoute, navigator.top)
        assertTrue(stored.nonRootUseDefault)
        assertEquals(!defaultUmount, stored.umountModules)
    }

    @Test
    fun failedSaveStaysOnModePageAndShowsRollbackAndError() {
        show()
        openMode()
        choose(R.string.profile_custom, 2)
        completeModeSave(success = false)
        assertEquals(modeRoute, navigator.top)
        assertTrue(stored.nonRootUseDefault)
        compose.onNodeWithText(compose.activity.getString(R.string.failed_to_update_app_profile, app.label)).assertExists()
        compose.onNodeWithText(text(R.string.profile_default)).assertIsSelected()
    }

    @Test
    fun lateSaveDuringBackDoesNotPopDetails() {
        show()
        openMode()
        choose(R.string.profile_custom, 2)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigator.back() }
        compose.mainClock.advanceTimeBy(32)
        completeModeSave()
        compose.mainClock.advanceTimeBy(1000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(listOf(WearRoute.Main, appRoute), stack.toList())
    }

    @Test
    fun restoredNonRootCustomPageReturnsPastOldModePage() {
        stored = stored.copy(nonRootUseDefault = false)
        stack.add(modeRoute)
        stack.add(customRoute)
        show()
        compose.waitForIdle()
        assertEquals(listOf(WearRoute.Main, appRoute), stack.toList())
        assertUmount(!defaultUmount)
        assertEquals(0, saves)
    }

    @Test
    fun authorizedCustomStillOpensRootFieldsAndTemplateChoice() {
        stored = stored.copy(allowSu = true)
        show()
        openMode()
        choose(R.string.profile_custom, 3)
        completeModeSave()
        assertEquals(customRoute, navigator.top)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText(text(R.string.wear_uid)).assertExists()
        compose.onNodeWithText(text(R.string.profile_umount_modules)).assertDoesNotExist()
        compose.runOnIdle { navigator.back() }
        compose.waitForIdle()
        choose(R.string.profile_template, 2)
        assertEquals(WearRoute.AppProfileTemplateChoice(app.uid, app.packageName), navigator.top)
    }

    private fun openMode() {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithText(text(R.string.wear_profile_mode)).performClick()
        compose.waitForIdle()
        assertEquals(modeRoute, navigator.top)
    }

    private fun choose(label: Int, index: Int) {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
        compose.onNodeWithText(text(label)).performClick()
        compose.waitForIdle()
    }

    private fun assertUmount(checked: Boolean) {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        val switch = compose.onNodeWithText(text(R.string.profile_umount_modules)).assertIsEnabled()
        if (checked) switch.assertIsOn() else switch.assertIsOff()
        assertEquals(checked, stored.umountModules)
    }

    private fun completeModeSave(success: Boolean = true) {
        compose.runOnIdle { mode.completeSave(success) }
        compose.waitForIdle()
    }

    private fun text(id: Int) = compose.activity.getString(id)

    private fun show() {
        detail = PageState()
        mode = PageState()
        custom = PageState()
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        Box(Modifier.requiredSize(216.dp)) {
                            NavDisplay(
                                backStack = stack,
                                onBack = navigator::back,
                                sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy()),
                                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                                entryProvider = withPageTransitions(
                                    entryProvider {
                                        entry<WearRoute.Main> { Box {} }
                                        entry<WearRoute.App> {
                                            val state by detail.state.collectAsStateWithLifecycle()
                                            WearAppDetail(group, false, navigator::back, navigator::open, state, detail.events) { action ->
                                                if (action == AppProfileUiAction.Load) detailLoads++
                                                detail.dispatch(action, saveImmediately = true)
                                            }
                                        }
                                        entry<WearRoute.AppProfileMode> { route ->
                                            val state by mode.state.collectAsStateWithLifecycle()
                                            WearAppProfileModeRoute(
                                                route.uid,
                                                route.packageName,
                                                app.label,
                                                onBack = { if (navigator.top == route) navigator.back() },
                                                onOpen = { if (navigator.top == route) navigator.open(it) },
                                                state = state,
                                                events = mode.events,
                                                onAction = { mode.dispatch(it) },
                                            )
                                        }
                                        entry<WearRoute.AppProfileCustom> { route ->
                                            val state by custom.state.collectAsStateWithLifecycle()
                                            WearAppProfileCustomRoute(
                                                route.uid, route.packageName, app.label,
                                                onBack = navigator::back, onOpen = navigator::open,
                                                templates = TemplateUiState(isLoading = false),
                                                onNonRootProfile = {
                                                    if (navigator.top == route) {
                                                        navigator.back()
                                                        if (navigator.top == modeRoute) navigator.back()
                                                    }
                                                },
                                                state = state, events = custom.events,
                                                onAction = { custom.dispatch(it, saveImmediately = true) },
                                            )
                                        }
                                        entry<WearRoute.AppProfileTemplateChoice> { Box {} }
                                    },
                                ) { _, content -> content() },
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    // Only persistence and ViewModel I/O are replaced; navigation and lifecycle refresh run normally.
    private inner class PageState {
        val state = MutableStateFlow(AppProfileUiState(profile = stored, defaultUmountModules = globalUmount, isLoading = false))
        val events = MutableSharedFlow<AppProfileUiEvent>(extraBufferCapacity = 1)
        private var pending: AppProfile? = null

        fun dispatch(action: AppProfileUiAction, saveImmediately: Boolean = false) {
            when (action) {
                AppProfileUiAction.Load -> state.value = state.value.copy(profile = stored, defaultUmountModules = globalUmount)

                is AppProfileUiAction.Save -> {
                    saves++
                    pending = action.profile
                    state.value = state.value.copy(profile = action.profile)
                    if (saveImmediately) completeSave(true)
                }

                else -> Unit
            }
        }

        fun completeSave(success: Boolean) {
            val profile = checkNotNull(pending)
            pending = null
            if (success) stored = profile
            state.value = state.value.copy(profile = stored)
            // A newly opened editor creates its own ViewModel and initially loads the saved value.
            if (success) custom.state.value = custom.state.value.copy(profile = stored)
            events.tryEmit(if (success) AppProfileUiEvent.Saved else AppProfileUiEvent.Error())
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, defaultUmount={1}")
        fun screens() = listOf(false, true).flatMap { round -> listOf(false, true).map { default -> arrayOf(round, default) } }
    }
}
