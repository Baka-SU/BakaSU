package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import java.util.Locale
import org.bakasu.bakasu.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearLanguageGroupingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun recommendationsPreserveRegionsScriptsAndSystemOrder() {
        fun recommend(vararg tags: String) = recommendedWearLanguages(tags.map(Locale::forLanguageTag)).map { it.toLanguageTag() }
        assertEquals(listOf("zh-TW", "en", "zh-CN"), recommend("zh-TW", "en-US", "zh-CN", "en-GB"))
        assertEquals(listOf("zh-HK"), recommend("zh-Hant-HK"))
        assertEquals(listOf("zh-CN"), recommend("zh-Hans-SG"))
        assertEquals(listOf("pt-BR", "pt"), recommend("pt-BR", "pt-PT"))
        assertEquals(emptyList<String>(), recommend("eo"))
    }

    @Test
    fun currentOtherLanguageStaysSelectedAndSystemDefaultRemainsAvailable() {
        val resources = compose.activity.resources
        var chosen: String? = null
        var selected by mutableStateOf(Locale.FRENCH)
        compose.setContent {
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    WearLanguagePage(selected, listOf(Locale.SIMPLIFIED_CHINESE, Locale.US), {}) { chosen = it }
                }
            }
        }
        val french = Locale.FRENCH.getDisplayName(Locale.FRENCH)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(french))
        compose.onNodeWithText(french).assertIsSelected()
        compose.onAllNodesWithText(french).assertCountEquals(1)
        compose.runOnIdle { selected = Locale.US }
        val english = Locale.ENGLISH.getDisplayName(Locale.ENGLISH)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(english))
        compose.onNodeWithText(english).assertIsSelected()
        val system = resources.getString(R.string.language_system_default)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(system))
        compose.onNodeWithText(system).performClick()
        compose.runOnIdle { assertEquals("system", chosen) }
    }
}
