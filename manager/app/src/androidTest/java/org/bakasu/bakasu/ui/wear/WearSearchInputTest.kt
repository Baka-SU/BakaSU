package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.usecase.SetStringPreferenceUseCase
import org.bakasu.bakasu.ui.wear.component.WearSearchPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

class WearSearchInputTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun holdingThenSwipingHistoryRevealsDeleteWithoutChangingTheDraft() {
        val scope = "test-delete-${System.nanoTime()}"
        GlobalContext.get().get<SetStringPreferenceUseCase>()("wear_search_history:$scope", "[\"old query\"]")
        val submitted = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                AppScaffold {
                    WearSearchPage(compose.activity.getString(R.string.search_modules), scope, "draft", {}, onSearch = { submitted.add(it) })
                }
            }
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToNode(hasText("old query"))
        compose.onNodeWithText("old query").performTouchInput {
            down(center)
            advanceEventTime(500)
            moveTo(Offset(center.x - 80, center.y))
            up()
        }
        compose.onNodeWithText("old query").assertExists()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.delete)).performClick()
        compose.onNodeWithText("old query").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.wear_search_history_empty)).assertExists()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToNode(hasSetTextAction())
        compose.onNodeWithText("draft").assertExists()
        compose.runOnIdle { assertEquals(emptyList<String>(), submitted) }
    }

    @Test
    fun typingIsInlineAndOnlyValidSubmissionsBecomeHistory() {
        val submitted = mutableListOf<String>()
        val scope = "test-input-${System.nanoTime()}"
        compose.setContent {
            MaterialTheme {
                AppScaffold {
                    WearSearchPage(compose.activity.getString(R.string.wear_search_input), scope, "", {}, onSearch = { submitted.add(it) })
                }
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.assertIsNotFocused()
        compose.onNodeWithText(compose.activity.getString(R.string.wear_search_history_empty)).assertExists()
        field.performClick()
        field.performTextInput("   ")
        field.performImeAction()
        compose.runOnIdle { assertEquals(emptyList<String>(), submitted) }
        field.performTextReplacement("  module  ")
        compose.runOnIdle { assertEquals(emptyList<String>(), submitted) }
        field.performImeAction()
        compose.runOnIdle { assertEquals(listOf("module"), submitted) }
        field.assertIsNotFocused()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToNode(hasText("module", substring = false))
        compose.onNodeWithText("module", substring = false).performClick()
        compose.runOnIdle { assertEquals(listOf("module", "module"), submitted) }
    }
}
