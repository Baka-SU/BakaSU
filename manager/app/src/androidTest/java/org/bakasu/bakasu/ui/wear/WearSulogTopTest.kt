package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.SulogEntry
import org.bakasu.bakasu.domain.model.SulogEventType
import org.bakasu.bakasu.ui.viewmodel.SulogUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearSulogTopTest(private val round: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun prependWhileAtTopKeepsHeaderStationary() {
        fun entry(id: Int) = SulogEntry("$id", SulogEventType.RootExecve, "seq=$id comm=Record-$id", null, mapOf("comm" to "Record-$id"))
        val records = (1..100).map(::entry)
        val state = mutableStateOf(SulogUiState(isLoading = false, isSulogEnabled = true, sulogStatus = "supported", selectedFilePath = "today", entries = records, visibleEntries = records.reversed()))
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
                screenWidthDp = 192
                screenHeightDp = 192
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                MaterialTheme { Box(Modifier.size(192.dp)) { AppScaffold(timeText = {}) { WearLogsPage(state.value, {}, {}, {}, onEntry = {}) } } }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        val title = compose.activity.getString(R.string.sulog)
        val before = compose.onNodeWithText(title).getUnclippedBoundsInRoot().top.value
        compose.runOnIdle {
            val updated = (2..101).map(::entry)
            state.value = state.value.copy(entries = updated, visibleEntries = updated.reversed())
        }
        val after = compose.onNodeWithText(title).getUnclippedBoundsInRoot().top.value
        assertEquals("Top must stay fixed when new logs arrive: before=$before after=$after", before, after, 1f)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}")
        fun configurations() = listOf(arrayOf(true), arrayOf(false))
    }
}
