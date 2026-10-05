package org.bakasu.bakasu.data.settings

import android.content.Context
import android.os.Build
import org.bakasu.bakasu.data.settings.launchSystemLanguageSettings as launchSystemLanguageSettingsInternal

class LocaleRepository(
    private val localeHelper: LocaleHelper,
) {
    fun applyLanguage(context: Context): Context = localeHelper.applyLanguage(context)
    fun isSystemLanguageSettings(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    fun launchSystemLanguageSettings(context: Context) = launchSystemLanguageSettingsInternal(context)
}
