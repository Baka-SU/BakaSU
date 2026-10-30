package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bakasu.bakasu.domain.usecase.GetStringPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.SetStringPreferenceUseCase
import org.json.JSONArray

/** Recent queries are kept separately for each feature and repository source. */
class WearSearchViewModel(
    scope: String,
    getPreference: GetStringPreferenceUseCase,
    private val setPreference: SetStringPreferenceUseCase,
) : ViewModel() {
    private val key = "wear_search_history:$scope"
    private val mutableHistory = MutableStateFlow(
        runCatching {
            val saved = JSONArray(getPreference(key, "[]"))
            List(saved.length()) { saved.getString(it) }.filter { it.isNotBlank() }.distinct().take(10)
        }.getOrDefault(emptyList()),
    )
    val history = mutableHistory.asStateFlow()

    fun record(query: String) {
        val value = query.trim()
        if (value.isEmpty()) return
        val recent = (listOf(value) + mutableHistory.value).distinct().take(10)
        mutableHistory.value = recent
        setPreference(key, JSONArray(recent).toString())
    }
}
