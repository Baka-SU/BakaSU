package org.bakasu.bakasu.ui.wear

import android.app.Application
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.module.ModuleCatalogRepository
import org.bakasu.bakasu.data.module.ModuleReleaseRepository
import org.bakasu.bakasu.data.module.RepositorySourceStore
import org.bakasu.bakasu.data.module.registry.KernelSuRegistryRepository
import org.bakasu.bakasu.data.network.NetworkRequestRepository
import org.bakasu.bakasu.data.network.NetworkStatusRepository
import org.bakasu.bakasu.domain.model.ModuleCatalogResult
import org.bakasu.bakasu.domain.model.RepositorySource
import org.bakasu.bakasu.domain.usecase.GetCatalogModuleUseCase
import org.bakasu.bakasu.domain.usecase.GetModuleReadmeUseCase
import org.bakasu.bakasu.domain.usecase.GetModuleReleaseDetailsUseCase
import org.bakasu.bakasu.domain.usecase.IsCatalogModuleCurrentUseCase
import org.bakasu.bakasu.domain.usecase.ObserveCatalogModulesUseCase
import org.bakasu.bakasu.domain.usecase.ObserveRepositorySourcesUseCase
import org.bakasu.bakasu.domain.usecase.RefreshModuleCatalogUseCase
import org.bakasu.bakasu.ui.viewmodel.ModuleDetailViewModel
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoUiState
import org.bakasu.bakasu.ui.wear.component.WearChip
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearLoadFailureScreen
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

class WearRepositoryLoadingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyManualRefreshUsesTheScreenCenterAndClearsWhenFinished() {
        val state = mutableStateOf(ModuleRepoUiState(isRefreshing = true, isPullRefreshing = true))
        compose.setContent {
            MaterialTheme {
                AppScaffold {
                    WearModuleRepoPage(state.value, {}, {}, {}) {}
                }
            }
        }
        val loading = compose.activity.getString(R.string.wear_loading)
        val indicator = compose.onNodeWithContentDescription(loading).getUnclippedBoundsInRoot()
        val viewport = compose.onRoot().getUnclippedBoundsInRoot()
        assertEquals((viewport.left.value + viewport.right.value) / 2, (indicator.left.value + indicator.right.value) / 2, 1f)
        assertEquals((viewport.top.value + viewport.bottom.value) / 2, (indicator.top.value + indicator.bottom.value) / 2, 1f)
        compose.runOnIdle { state.value = state.value.copy(isRefreshing = false, isPullRefreshing = false) }
        compose.onNodeWithContentDescription(loading).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.repo_source_empty)).assertExists()
    }

    @Test
    fun failureScreenShowsTheReasonAndSupportsRetry() {
        val context = compose.activity
        val reason = context.getString(R.string.wear_no_internet)
        var retries = 0
        compose.setContent {
            MaterialTheme {
                AppScaffold {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        WearLoadFailureScreen(reason, {}, { retries++ })
                    }
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.wear_load_failed)).assertIsDisplayed()
        compose.onNodeWithText(reason).assertIsDisplayed()
        saveScreen("repository-load-failure")
        val retry = context.getString(R.string.network_retry)
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.ScrollToIndex)).performScrollToNode(hasText(retry))
        compose.onNodeWithText(retry).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun refreshKeepsTheContentAndShowsAContainedLoadingIndicator() {
        val refreshing = mutableStateOf(false)
        val context = compose.activity
        val label = context.getString(R.string.module_repo)
        compose.setContent {
            MaterialTheme {
                AppScaffold {
                    WearList(isRefreshing = refreshing.value, onBack = {}) { spec ->
                        item { WearPageHeader(spec, label) }
                        item { WearChip(spec, stringResource(R.string.readme)) }
                    }
                }
            }
        }
        compose.runOnIdle { refreshing.value = true }
        compose.onNodeWithText(label).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.wear_loading)).assertIsDisplayed()
        val titleBounds = compose.onNodeWithText(label).getUnclippedBoundsInRoot()
        val indicatorBounds = compose.onNodeWithContentDescription(context.getString(R.string.wear_loading)).getUnclippedBoundsInRoot()
        assertTrue("Refresh must follow the measured title", indicatorBounds.top >= titleBounds.bottom)
        assertTrue("Refresh must precede the first row", indicatorBounds.bottom <= compose.onNodeWithText(context.getString(R.string.readme)).getUnclippedBoundsInRoot().top)
        saveScreen("repository-refresh")
        compose.runOnIdle { refreshing.value = false }
        compose.onNodeWithContentDescription(context.getString(R.string.wear_loading)).assertDoesNotExist()
        compose.onNodeWithText(label).assertIsDisplayed()
    }

    @Test
    fun failureScreenCanRetryOnSmallRoundAndSquareScreensWithLargeText() {
        val round = mutableStateOf(true)
        val context = compose.activity
        var retries = 0
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round.value) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
                screenWidthDp = 192
                screenHeightDp = 192
                fontScale = 1.3f
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp)) {
                        AppScaffold {
                            key(round.value) { WearLoadFailureScreen(context.getString(R.string.wear_no_internet), {}, { retries++ }) }
                        }
                    }
                }
            }
        }
        listOf(true, false).forEach { isRound ->
            compose.runOnIdle { round.value = isRound }
            compose.onNodeWithText(context.getString(R.string.wear_load_failed)).assertExists()
            val retry = context.getString(R.string.network_retry)
            compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.ScrollToIndex)).performScrollToNode(hasText(retry))
            compose.onNodeWithText(retry).performClick()
        }
        compose.runOnIdle { assertEquals(2, retries) }
    }

    @Test
    fun loadedCatalogDetailAndReadmeDoNotSurviveAFailedRequestAndRecoverOnRetry() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val cacheDir = File(application.cacheDir, "repository-loading-test-${System.nanoTime()}").apply { mkdirs() }
        val registryApplication = object : Application() {
            override fun getFilesDir() = cacheDir
        }
        val sourceStore = GlobalContext.get().get<RepositorySourceStore>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val store = ViewModelStore()
        var offline = false
        var introduction = "<p>First introduction</p>"
        val fields = """"id":"example","name":"Example","stable":{"version":"1","versionCode":1,"publishedAt":"2026-11-06"},"updatedAt":"2026-11-06""""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            if (offline) throw IOException("Disconnected")
            val body = when {
                chain.request().url.encodedPath.endsWith("index.json") -> """{"schemaVersion":1,"buildId":"one","modules":"api/v1/modules.json"}"""
                chain.request().url.encodedPath.endsWith("modules.json") -> """{"schemaVersion":1,"buildId":"one","modules":[{$fields,"detail":"api/v1/modules/example.json"}]}"""
                chain.request().url.encodedPath.endsWith("example.json") -> """{"schemaVersion":1,"buildId":"one",$fields,"readme":"api/v1/modules/example/readme"}"""
                else -> introduction
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val network = NetworkRequestRepository(client)
        val repository = ModuleCatalogRepository(network, sourceStore, KernelSuRegistryRepository(registryApplication, client), NetworkStatusRepository(application))
        lateinit var viewModel: ModuleDetailViewModel
        fun openDetail() {
            compose.runOnIdle {
                store.clear()
                viewModel = ModuleDetailViewModel(
                    "example", RepositorySource.KERNEL_SU_URL, GetCatalogModuleUseCase(repository), GetModuleReadmeUseCase(repository),
                    GetModuleReleaseDetailsUseCase(ModuleReleaseRepository(network, scope)), ObserveCatalogModulesUseCase(repository),
                    ObserveRepositorySourcesUseCase(repository), IsCatalogModuleCurrentUseCase(repository), RefreshModuleCatalogUseCase(repository),
                )
                store.put("detail", viewModel)
            }
            compose.waitUntil(10_000) { !viewModel.state.value.loading }
        }
        fun readme() {
            compose.runOnIdle { viewModel.loadReadme(retry = true) }
            compose.waitUntil(10_000) { viewModel.state.value.documents.values.singleOrNull()?.loading == false }
        }
        try {
            openDetail()
            assertNotNull(viewModel.state.value.module)
            readme()
            assertEquals(introduction, viewModel.state.value.documents.values.single().document?.content)
            introduction = "<p>Refreshed introduction</p>"
            compose.runOnIdle {
                viewModel.refresh()
                assertNotNull(viewModel.state.value.documents.values.single().document)
            }
            compose.waitUntil(10_000) {
                !viewModel.state.value.loading && viewModel.state.value.documents.values.singleOrNull()?.document?.content == introduction
            }
            offline = true
            compose.runOnIdle {
                viewModel.loadReadme(retry = true)
                assertEquals(introduction, viewModel.state.value.documents.values.single().document?.content)
            }
            compose.waitUntil(10_000) { viewModel.state.value.documents.values.singleOrNull()?.loading == false }
            assertTrue(viewModel.state.value.documents.values.single().failed)
            assertNull(viewModel.state.value.documents.values.single().document)
            openDetail()
            assertNotNull(viewModel.state.value.error)
            assertNull(viewModel.state.value.module)
            assertTrue(repository.modules.value.isEmpty())
            assertTrue(runBlocking { repository.refresh() } is ModuleCatalogResult.Failure)
            offline = false
            introduction = "<p>Updated introduction</p>"
            compose.runOnIdle { viewModel.refresh() }
            compose.waitUntil(10_000) { !viewModel.state.value.loading }
            assertNull(viewModel.state.value.error)
            assertFalse(repository.modules.value.isEmpty())
            readme()
            assertEquals(introduction, viewModel.state.value.documents.values.single().document?.content)
        } finally {
            compose.runOnIdle { store.clear() }
            scope.cancel()
            client.connectionPool.evictAll()
            cacheDir.deleteRecursively()
        }
    }

    private fun saveScreen(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
