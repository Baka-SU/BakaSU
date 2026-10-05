package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.domain.text.TextTransliterator

class TransliterateTextUseCase(private val transliterator: TextTransliterator) {
    operator fun invoke(value: String): String = transliterator.transliterate(value)
}
