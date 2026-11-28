package org.bakasu.bakasu.domain.text

fun interface TextTransliterator {
    fun transliterate(value: String): String
}
