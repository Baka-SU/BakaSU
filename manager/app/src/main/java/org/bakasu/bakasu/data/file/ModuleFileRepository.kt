package org.bakasu.bakasu.data.file

import android.app.Application
import androidx.core.net.toUri
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModuleFileRepository(
    private val application: Application,
) {
    private val moduleUtils = ModuleUtils()

    fun isUriAccessible(uri: String): Boolean = moduleUtils.isUriAccessible(application, uri.toUri())

    fun takePersistableUriPermission(uri: String) {
        moduleUtils.takePersistableUriPermission(application, uri.toUri())
    }

    fun extractModuleName(uri: String): String = moduleUtils.extractModuleName(application, uri.toUri())

    fun extractModuleId(uri: String): String? = moduleUtils.extractModuleId(application, uri.toUri())

    /** Whether the file at [uri] has the published [size] and SHA-256 [sha256] (hex, any case). */
    suspend fun matchesDigest(uri: String, size: Long, sha256: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            var length = 0L
            checkNotNull(application.contentResolver.openInputStream(uri.toUri())).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    length += read
                }
            }
            length == size && digest.digest().joinToString("") { "%02x".format(it) }.equals(sha256, ignoreCase = true)
        }.getOrDefault(false)
    }
}
