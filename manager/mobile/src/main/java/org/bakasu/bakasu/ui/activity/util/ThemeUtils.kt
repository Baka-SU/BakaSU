package org.bakasu.bakasu.ui.activity.util

import org.bakasu.bakasu.data.theme.ThemeRepository
import org.bakasu.bakasu.ui.theme.BackgroundManager
import org.bakasu.bakasu.ui.theme.CardConfig
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.viewmodel.SettingsUiAction
import org.bakasu.bakasu.ui.viewmodel.SettingsViewModel

class ThemeUtils(
    private val themeConfig: ThemeConfig,
    private val themeRepository: ThemeRepository,
    private val cardConfig: CardConfig,
    private val backgroundManager: BackgroundManager,
) {

    fun initializeThemeSettings(settingsViewModel: SettingsViewModel) {
        settingsViewModel.dispatch(SettingsUiAction.InitializeFirstRun)
        loadThemeSettings()
        settingsViewModel.dispatch(SettingsUiAction.Initialize)
    }

    fun onActivityPause() {
        cardConfig.save()
    }

    fun onActivityResume() {
        loadThemeSettings()
    }

    private fun loadThemeSettings() {
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
