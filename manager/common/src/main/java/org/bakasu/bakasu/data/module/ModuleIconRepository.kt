package org.bakasu.bakasu.data.module

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bakasu.bakasu.data.module.registry.RegistryUrls

/** Bounded raster loading adapted from tiann/KernelSU's ModuleRepoScreen.kt. */
class ModuleIconRepository(httpClient: OkHttpClient) {
    private val client = httpClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val downloads = Semaphore(4)
    private val failures = LruCache<String, Long>(128)
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun cached(url: String, origin: String?): Bitmap? = if (allowed(url, origin)) cache.get(key(url, origin)) else null

    suspend fun load(url: String, origin: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (!allowed(url, origin)) return@withContext null
        val key = key(url, origin)
        cache.get(key)?.let { return@withContext it }
        if ((failures.get(key) ?: 0L) > SystemClock.elapsedRealtime()) return@withContext null
        downloads.withPermit {
            cache.get(key)?.let { return@withPermit it }
            if ((failures.get(key) ?: 0L) > SystemClock.elapsedRealtime()) return@withPermit null
            val bitmap = try {
                fetch(url, origin)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (bitmap != null) {
                cache.put(key, bitmap)
                failures.remove(key)
            } else {
                failures.put(key, SystemClock.elapsedRealtime() + 60_000)
            }
            bitmap
        }
    }

    private fun key(url: String, origin: String?) = origin.orEmpty() + "|" + url

    private fun allowed(value: String, origin: String?): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.isHttps && url.username.isEmpty() && url.password.isEmpty() &&
            (origin == null || RegistryUrls.isAllowedRestrictedResource(value, origin))
    }

    private fun fetch(url: String, origin: String?): Bitmap? {
        var current = url
        val deadline = SystemClock.elapsedRealtime() + 10_000
        repeat(6) {
            if (!allowed(current, origin)) return null
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) return null
            val call = client.newCall(Request.Builder().url(current).build())
            call.timeout().timeout(remaining, TimeUnit.MILLISECONDS)
            call.execute().use { response ->
                if (response.isRedirect) {
                    // Registry assets are content-hashed and never follow redirects.
                    if (origin != null) return null
                    current = response.header("Location")?.let { response.request.url.resolve(it) }?.toString() ?: return null
                } else {
                    if (!response.isSuccessful) return null
                    val source = response.body?.source() ?: return null
                    if (source.request(512L * 1024 + 1)) return null
                    val bytes = source.readByteArray()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 192) sample *= 2
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
        }
        return null
    }
}
