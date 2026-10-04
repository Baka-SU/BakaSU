package com.bakasu.bakasu.domain.usecase

import android.content.Context
import com.bakasu.bakasu.data.settings.LocaleRepository

class ApplyLanguageUseCase(private val repository: LocaleRepository) {
    operator fun invoke(context: Context): Context = repository.applyLanguage(context)
}

class IsSystemLanguageSettingsUseCase(private val repository: LocaleRepository) {
    operator fun invoke(): Boolean = repository.isSystemLanguageSettings()
}

class LaunchSystemLanguageSettingsUseCase(private val repository: LocaleRepository) {
    operator fun invoke(context: Context) = repository.launchSystemLanguageSettings(context)
}
