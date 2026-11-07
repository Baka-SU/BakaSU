package org.bakasu.bakasu.data.profile

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.bakasu.bakasu.data.network.NetworkRequestRepository
import org.bakasu.bakasu.data.network.NetworkStatusRepository
import org.bakasu.bakasu.data.shell.KsuCliRepository
import org.bakasu.bakasu.domain.model.ProfileTemplateException
import org.bakasu.bakasu.domain.model.ProfileTemplateFailure
import org.bakasu.bakasu.domain.usecase.AddOnlineProfileTemplateUseCase
import org.bakasu.bakasu.domain.usecase.FetchOnlineProfileTemplatesUseCase
import org.bakasu.bakasu.domain.usecase.ObserveProfileTemplatesUseCase
import org.bakasu.bakasu.ui.viewmodel.OnlineTemplateUiAction
import org.bakasu.bakasu.ui.viewmodel.OnlineTemplateViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineProfileTemplatesTest {
    @Test
    fun refreshFailureKeepsPreviouslyLoadedTemplatesAndCanRetry() = runBlocking {
        val responses = java.util.concurrent.ConcurrentHashMap(
            mapOf("index.json" to "[\"working\"]", "working" to """{"id":"working","name":"Working","description":"Template"}"""),
        )
        val repository = repository(responses)
        val store = ViewModelStore()
        val model = withContext(Dispatchers.Main) {
            OnlineTemplateViewModel(ObserveProfileTemplatesUseCase(repository), FetchOnlineProfileTemplatesUseCase(repository), AddOnlineProfileTemplateUseCase(repository))
                .also { store.put("online", it) }
        }
        try {
            val loaded = withTimeout(5_000) { model.state.first { it.hasLoaded && !it.loading } }
            responses.remove("working")
            withContext(Dispatchers.Main) { model.dispatch(OnlineTemplateUiAction.Load) }
            val failed = withTimeout(5_000) { model.state.first { it.loadFailure != null } }
            assertEquals(loaded.templates, failed.templates)
            assertFalse(failed.isLoading)
            assertFalse(failed.isRefreshing)
            responses["index.json"] = "[]"
            withContext(Dispatchers.Main) { model.dispatch(OnlineTemplateUiAction.Load) }
            val retried = withTimeout(5_000) { model.state.first { !it.loading && it.loadFailure == null && it.templates.isEmpty() } }
            assertTrue(retried.hasLoaded)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    @Test
    fun emptyIndexIsSuccessfulAndBrowsingDoesNotSaveTemplates() = runBlocking {
        val repository = repository(mapOf("index.json" to "[]"))
        assertEquals(emptyList<Any>(), repository.fetchOnline().getOrThrow())
        assertTrue(repository.templates.value.isEmpty())
    }

    @Test
    fun nonemptyIndexWithEveryDownloadFailingIsNotAnEmptyDirectory() = runBlocking {
        val result = repository(mapOf("index.json" to "[\"missing\"]")).fetchOnline()
        assertTrue(result.isFailure)
        assertTrue((result.exceptionOrNull() as ProfileTemplateException).reason in listOf(ProfileTemplateFailure.RequestFailed, ProfileTemplateFailure.Offline))
    }

    @Test
    fun malformedIndexAndUnparseableTemplatesAreInvalid() = runBlocking {
        for (responses in listOf(
            mapOf("index.json" to "invalid"),
            mapOf("index.json" to "[\"broken\"]", "broken" to "{}"),
        )) {
            val error = repository(responses).fetchOnline().exceptionOrNull() as ProfileTemplateException
            assertEquals(ProfileTemplateFailure.Invalid, error.reason)
        }
    }

    @Test
    fun partialSuccessRemainsBrowsableWithoutSaving() = runBlocking {
        val repository = repository(
            mapOf(
                "index.json" to "[\"missing\",\"working\"]",
                "working" to """{"id":"working","name":"Working","description":"Template","local":true}""",
            ),
        )
        val templates = repository.fetchOnline().getOrThrow()
        assertEquals(listOf("working"), templates.map { it.id })
        assertFalse(templates.single().local)
        assertTrue(repository.templates.value.isEmpty())
    }

    private fun repository(responses: Map<String, String>): ProfileTemplateRepository {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = responses[chain.request().url.pathSegments.last()]
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (body == null) 503 else 200).message("Test response")
                .body((body ?: "Unavailable").toResponseBody()).build()
        }.build()
        return ProfileTemplateRepository(NetworkStatusRepository(application), NetworkRequestRepository(client), KsuCliRepository(application))
    }
}
