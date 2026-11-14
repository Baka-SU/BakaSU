package org.bakasu.bakasu.ui.wear.component

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    private val iconHost = "https://icons-${System.nanoTime()}.example"

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun concurrentRequestsShareOneTransferAndColdLoadsUseDisk() = runBlocking {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val requests = AtomicInteger()
        val release = java.util.concurrent.CountDownLatch(1)
        val started = java.util.concurrent.CountDownLatch(1)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            started.countDown()
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test").body(bytes.toResponseBody()).build()
        }.build()
        try {
            val repository = ModuleIconRepository(client, compose.activity.application, scope)
            val url = "$iconHost/concurrent.png"
            val loads = List(8) { async(kotlinx.coroutines.Dispatchers.Default) { repository.load(url, null) } }
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS))
            delay(100)
            release.countDown()
            assertTrue(loads.awaitAll().all { it != null })
            assertEquals(1, requests.get())
            val offline = ModuleIconRepository(OkHttpClient.Builder().addInterceptor { error("Disk cache must not access the network") }.build(), compose.activity.application, scope)
            assertEquals(android.graphics.Color.GREEN, offline.load(url, null)?.getPixel(0, 0))
        } finally {
            release.countDown()
            scope.cancel()
        }
    }

    @Test
    fun smallAndLargeTilesKeepSquareCornersWhiteTransparencyAndFilledEdges() {
        fun png(kind: String): ByteArray {
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.GREEN }
            when (kind) {
                "solid" -> canvas.drawColor(android.graphics.Color.GREEN)
                "rounded" -> canvas.drawRoundRect(0f, 0f, 100f, 100f, 45f, 45f, paint)
                "padded" -> canvas.drawRoundRect(4f, 4f, 96f, 96f, 28f, 28f, paint)
                "transparent" -> canvas.drawCircle(50f, 50f, 12f, paint)
            }
            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }
        val images = listOf("solid", "padded", "transparent", "rounded").associateWith(::png)
        val repository = ModuleIconRepository(
            OkHttpClient.Builder().addInterceptor { chain ->
                val bytes = images.getValue(chain.request().url.encodedPath.removePrefix("/"))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test").body(bytes.toResponseBody()).build()
            }.build(),
            compose.activity.application,
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
        )
        runBlocking { images.keys.forEach { repository.load("$iconHost/$it", null) } }
        val application = koinApplication { modules(module { single { LoadModuleIconUseCase(repository) } }) }
        try {
            compose.setContent {
                KoinIsolatedContext(application) {
                    MaterialTheme {
                        Column(Modifier.background(Color.Black)) {
                            listOf(24, 40, 52).forEach { size ->
                                Row {
                                    images.keys.forEach { kind ->
                                        Box(Modifier.testTag("$kind-$size")) {
                                            WearCatalogModuleIcon(if (kind == "missing") null else "$iconHost/$kind", null, size.dp)
                                        }
                                    }
                                }
                            }
                            Row {
                                listOf(24, 40, 52).forEach { size ->
                                    Box(Modifier.testTag("missing-$size")) { WearCatalogModuleIcon(null, null, size.dp) }
                                }
                            }
                        }
                    }
                }
            }
            listOf(24, 40, 52).forEach { size ->
                val solid = compose.onNodeWithTag("solid-$size").captureToImage().toPixelMap()
                // This point lies inside the proportional rounded square and outside a circle.
                assertTrue("$size dp must not be circular", solid[(solid.width * 0.25f).toInt(), (solid.height * 0.06f).toInt()].green > 0.9f)
                val padded = compose.onNodeWithTag("padded-$size").captureToImage().toPixelMap()
                val edge = padded[padded.width / 2, 1]
                assertTrue("The small transparent outer border must be filled", edge.green > 0.7f && edge.red > 0.7f)
                val rounded = compose.onNodeWithTag("rounded-$size").captureToImage().toPixelMap()
                val corner = rounded[(rounded.width * 0.12f).toInt(), (rounded.height * 0.12f).toInt()]
                assertTrue("Rounder supplied corners must also fill the tile", corner.green > 0.7f && corner.red > 0.7f)
                val transparent = compose.onNodeWithTag("transparent-$size").captureToImage().toPixelMap()
                val base = transparent[transparent.width / 2, (transparent.height * 0.2f).toInt()]
                assertTrue("Monochrome artwork keeps a transparent themed tile", base.red < 0.1f && base.green < 0.1f && base.blue < 0.1f)
            }
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(compose.activity.getExternalFilesDir(null), "fixed-icon-sizes.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            application.close()
        }
    }

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
            compose.activity.application,
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
        )
        runBlocking { repository.load("$iconHost/ok.png", null) }
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
            compose.runOnIdle { url.value = "$iconHost/ok.png" }
            assertTrue(center().green > 0.95f && center().red < 0.05f)
            assertEquals(1, requests.get())
            compose.runOnIdle { url.value = "$iconHost/missing.png" }
            compose.waitUntil(5_000) { requests.get() == 2 }
            assertEquals(fallback, center())
            compose.runOnIdle { url.value = "http://icons.example/ok.png" }
            assertEquals(fallback, center())
            compose.runOnIdle {
                url.value = "$iconHost/ok.png"
                origin.value = "https://another.example/"
            }
            assertEquals(fallback, center())
            assertEquals(2, requests.get())
        } finally {
            application.close()
        }
    }
}
