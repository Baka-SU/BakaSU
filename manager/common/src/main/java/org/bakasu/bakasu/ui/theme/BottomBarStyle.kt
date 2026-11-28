package org.bakasu.bakasu.ui.theme

enum class BottomBarStyle {
    MATERIAL3_EXPRESSIVE,
    FLOATING,
    ;

    companion object {
        fun fromOrdinal(ordinal: Int): BottomBarStyle = entries.getOrElse(ordinal) { MATERIAL3_EXPRESSIVE }
    }
}
