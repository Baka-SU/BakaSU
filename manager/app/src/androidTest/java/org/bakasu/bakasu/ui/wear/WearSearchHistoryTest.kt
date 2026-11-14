package org.bakasu.bakasu.ui.wear

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.bakasu.bakasu.data.AppSettingsRepository
import org.bakasu.bakasu.domain.usecase.GetStringPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.SetStringPreferenceUseCase
import org.bakasu.bakasu.ui.viewmodel.WearSearchViewModel
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.core.context.GlobalContext

class WearSearchHistoryTest {
    @Test
    fun deletionPersistsWithoutChangingOtherScopesAndQueriesCanBeRecordedAgain() = runBlocking {
        val repository = GlobalContext.get().get<AppSettingsRepository>()
        repository.preload()
        repository.remove("wear_search_history:test-first")
        repository.remove("wear_search_history:test-other")
        fun model(scope: String) = WearSearchViewModel(scope, GetStringPreferenceUseCase(repository), SetStringPreferenceUseCase(repository))
        suspend fun saved(scope: String, expected: List<String>) {
            withTimeout(5000) {
                do {
                    delay(50)
                    repository.preload()
                } while (repository.getString("wear_search_history:$scope") != JSONArray(expected).toString())
            }
            assertEquals(expected, model(scope).history.value)
        }
        val first = model("test-first")
        val other = model("test-other")
        first.record("old")
        saved("test-first", listOf("old"))
        first.record("middle")
        saved("test-first", first.history.value)
        first.record("new")
        saved("test-first", first.history.value)
        other.record("middle")
        saved("test-other", listOf("middle"))
        first.remove(" middle ")
        saved("test-first", listOf("new", "old"))
        assertEquals(listOf("new", "old"), model("test-first").history.value)
        assertEquals(listOf("middle"), model("test-other").history.value)
        first.remove("old")
        saved("test-first", listOf("new"))
        first.remove("new")
        saved("test-first", emptyList())
        assertEquals(emptyList<String>(), model("test-first").history.value)
        first.record(" middle ")
        saved("test-first", listOf("middle"))
        first.record("middle")
        saved("test-first", first.history.value)
        assertEquals(listOf("middle"), model("test-first").history.value)
        repeat(12) {
            first.record("query-$it")
            saved("test-first", first.history.value)
        }
        assertEquals((11 downTo 2).map { "query-$it" }, model("test-first").history.value)
        repository.remove("wear_search_history:test-first")
        repository.remove("wear_search_history:test-other")
    }
}
