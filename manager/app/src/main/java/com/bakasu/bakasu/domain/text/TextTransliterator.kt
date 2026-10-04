package com.bakasu.bakasu.domain.text

fun interface TextTransliterator {
    fun transliterate(value: String): String
}
