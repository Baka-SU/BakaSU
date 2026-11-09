package org.bakasu.bakasu.ui.wear.component

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.bakasu.bakasu.data.module.ModuleIconRepository
import org.bakasu.bakasu.domain.usecase.LoadModuleIconUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class WearCatalogModuleIconTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cachedModuleIconsAndMissingFailedOrRestrictedUrlsUseTheSameTile() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val requests = AtomicInteger()
        val repository = ModuleIconRepository(
            OkHttpClient.Builder().addInterceptor { chain ->
                requests.incrementAndGet()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(if (chain.request().url.encodedPath == "/ok.png") 200 else 404).message("test").body(bytes.toResponseBody()).build()
            }.build(),
        )
        runBlocking { repository.load("https://icons.example/ok.png", null) }
        val application = koinApplication { modules(module { single { LoadModuleIconUseCase(repository) } }) }
        val url = mutableStateOf<String?>(null)
        val origin = mutableStateOf<String?>(null)
        try {
            compose.setContent {
                KoinIsolatedContext(application) {
                    MaterialTheme {
                        Box(Modifier.size(48.dp).testTag("icon")) { WearCatalogModuleIcon(url.value, origin.value, 48.dp) }
                    }
                }
            }
            fun center() = compose.onNodeWithTag("icon").captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }
            val fallback = center()
            compose.runOnIdle { url.value = "https://icons.example/ok.png" }
            assertTrue(center().green > 0.95f && center().red < 0.05f)
            assertEquals(1, requests.get())
            compose.runOnIdle { url.value = "https://icons.example/missing.png" }
            compose.waitUntil(5_000) { requests.get() == 2 }
            assertEquals(fallback, center())
            compose.runOnIdle { url.value = "http://icons.example/ok.png" }
            assertEquals(fallback, center())
            compose.runOnIdle {
                url.value = "https://icons.example/ok.png"
                origin.value = "https://another.example/"
            }
            assertEquals(fallback, center())
            assertEquals(2, requests.get())
        } finally {
            application.close()
        }
    }
}
