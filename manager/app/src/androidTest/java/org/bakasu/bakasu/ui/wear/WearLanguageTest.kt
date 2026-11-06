package org.bakasu.bakasu.ui.wear

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.bakasu.bakasu.data.settings.LocaleHelper
import org.bakasu.bakasu.domain.model.PlatformSetting
import org.bakasu.bakasu.domain.usecase.UpdatePlatformSettingUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.koin.core.context.GlobalContext

class WearLanguageTest {
    @Test
    fun sharedLanguageSettingSupportsRegionalTagsAndSystemDefault() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = GlobalContext.get().get<LocaleHelper>()
        val update = GlobalContext.get().get<UpdatePlatformSettingUseCase>()
        val original = helper.getCurrentAppLocale(context)?.toLanguageTag() ?: "system"
        try {
            for ((tag, expected) in listOf("zh_CN" to "zh-CN", "pt_BR" to "pt-BR", "en" to "en")) {
                assertEquals(expected, update(PlatformSetting.Locale(tag)).getOrThrow().currentLocaleTag)
                assertEquals(expected, helper.getCurrentAppLocale(context)?.toLanguageTag())
                assertEquals(expected, helper.applyLanguage(context).resources.configuration.locales[0].toLanguageTag())
            }
            assertNull(update(PlatformSetting.Locale("system")).getOrThrow().currentLocaleTag)
            assertNull(helper.getCurrentAppLocale(context))
        } finally {
            update(PlatformSetting.Locale(original)).getOrThrow()
        }
    }
}
