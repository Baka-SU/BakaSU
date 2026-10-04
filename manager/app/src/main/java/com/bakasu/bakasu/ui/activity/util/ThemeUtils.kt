package com.bakasu.bakasu.ui.activity.util

import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings
import com.bakasu.bakasu.data.AppSettingsRepository
import com.bakasu.bakasu.data.theme.ThemeRepository
import com.bakasu.bakasu.ui.MainActivity
import com.bakasu.bakasu.ui.theme.BackgroundManager
import com.bakasu.bakasu.ui.theme.CardConfig
import com.bakasu.bakasu.ui.theme.ThemeConfig
import com.bakasu.bakasu.ui.viewmodel.SettingsUiAction
import com.bakasu.bakasu.ui.viewmodel.SettingsViewModel

class ThemeChangeContentObserver(
    handler: Handler,
    private val onThemeChanged: () -> Unit
) : ContentObserver(handler) {
    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        onThemeChanged()
    }
}

class ThemeUtils(
    private val settings: AppSettingsRepository,
    private val themeConfig: ThemeConfig,
    private val themeRepository: ThemeRepository,
    private val cardConfig: CardConfig,
    private val backgroundManager: BackgroundManager,
) {

    fun initializeThemeSettings(activity: MainActivity, settingsViewModel: SettingsViewModel) {
        settingsViewModel.dispatch(SettingsUiAction.InitializeFirstRun)
        loadThemeSettings(activity)
        settingsViewModel.dispatch(SettingsUiAction.Initialize)
    }

    fun registerThemeChangeObserver(activity: MainActivity): ThemeChangeContentObserver {
        val contentObserver = ThemeChangeContentObserver(Handler(activity.mainLooper)) {
            activity.runOnUiThread {
                if (!themeConfig.preventBackgroundRefresh) {
                    themeConfig.backgroundImageLoaded = false
                    backgroundManager.loadCustomBackground()
                }
            }
        }

        activity.contentResolver.registerContentObserver(
            Settings.System.getUriFor("ui_night_mode"),
            false,
            contentObserver
        )

        return contentObserver
    }

    fun unregisterThemeChangeObserver(activity: MainActivity, observer: ThemeChangeContentObserver) {
        activity.contentResolver.unregisterContentObserver(observer)
    }

    fun onActivityPause() {
        cardConfig.save()
        settings.putBoolean("prevent_background_refresh", true)
        themeConfig.preventBackgroundRefresh = true
    }

    fun onActivityResume(activity: MainActivity) {
        settings.putBoolean("prevent_background_refresh", false)
        themeConfig.preventBackgroundRefresh = false
        loadThemeSettings(activity)
    }

    private fun loadThemeSettings(activity: MainActivity) {
        themeConfig.forceDarkMode = themeRepository.loadThemeMode()
        themeConfig.seedColor = themeRepository.loadSeedColor()
        themeConfig.useDynamicColor = themeRepository.loadDynamicColorState()
        themeConfig.dynamicColorSpec = themeRepository.loadDynamicColorSpec()
        themeConfig.dynamicPaletteStyle = themeRepository.loadDynamicPaletteStyle(
            themeConfig.dynamicColorSpec,
        )
        cardConfig.load()
        backgroundManager.loadCustomBackground()
    }
}
