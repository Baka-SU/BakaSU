package com.resukisu.resukisu.data.file

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.logging.BugreportRepository
import com.resukisu.resukisu.data.shell.KsuCliRepository
import com.resukisu.resukisu.ui.webui.MimeUtil
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class WearFileEntry(val path: String, val name: String, val directory: Boolean, val size: Long)
data class WearDirectory(val path: String, val parent: String?, val entries: List<WearFileEntry>, val writable: Boolean)

/** A failure whose message is already a localized, user-facing resource string. */
class WearFileException(message: String) : Exception(message)

/** The MIME type of a file name, as served by [WearFileProvider]; unknown types are generic binary data. */
fun wearFileMimeType(name: String): String = MimeUtil.getMimeFromFileName(name) ?: "application/octet-stream"

/** Whether [mimeType] is accepted by one of the requested [filters], which may use wildcard types. */
fun matchesMimeType(mimeType: String, filters: List<String>): Boolean = filters.any { filter ->
    filter == "*/*" || filter.equals(mimeType, ignoreCase = true) ||
        filter.endsWith("/*") && mimeType.startsWith(filter.dropLast(1), ignoreCase = true)
}

class WearFileRepository(
    private val application: Application,
    private val cli: KsuCliRepository,
    private val bugreport: BugreportRepository,
) {
    private fun fail(resource: Int): Nothing = throw WearFileException(application.getString(resource))

    private val stagingDirectory get() = File(application.cacheDir, "wear-picker").apply { mkdirs() }

    /** Lists [path] (or a default storage folder) with the files whose MIME type matches [mimeTypes]. */
    suspend fun list(path: String?, mimeTypes: List<String>, directoriesOnly: Boolean): WearDirectory = withContext(Dispatchers.IO) {
        cli.withNewRootShell(true) {
        val operationShell = this
        fun file(path: String) = SuFile(path).apply { shell = operationShell }
        val directory = path?.let(::file) ?: listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path,
            Environment.getExternalStorageDirectory().path,
            application.getExternalFilesDir(null)?.path,
            application.filesDir.path,
        ).filterNotNull().map(::file).firstOrNull { it.isDirectory && it.canRead() }
            ?: fail(R.string.wear_directory_unavailable)
        val children = directory.listFiles() ?: fail(R.string.wear_directory_unavailable)
        WearDirectory(directory.path, directory.parent, children.filter { child ->
            child.canRead() && (child.isDirectory ||
                !directoriesOnly && matchesMimeType(wearFileMimeType(child.name), mimeTypes))
        }.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .map { WearFileEntry(it.path, it.name, it.isDirectory, if (it.isDirectory) 0 else it.length()) }, directory.canWrite())
        }
    }

    /** The path of a new file [name] in [directory], checked to be a plain name that does not exist yet. */
    suspend fun newFile(directory: String, name: String): String = withContext(Dispatchers.IO) {
        if (name.isBlank() || name == "." || name == ".." || '/' in name || '\\' in name) fail(R.string.operation_failed)
        cli.withNewRootShell(true) {
            val target = SuFile(File(directory, name).path).apply { shell = this@withNewRootShell }
            if (target.exists()) fail(R.string.wear_file_exists)
            target.path
        }
    }

    /** Root paths must be staged before the app can read them through a file descriptor. */
    suspend fun stage(path: String): File = withContext(Dispatchers.IO) {
        cli.withNewRootShell(true) {
        val operationShell = this
        val source = SuFile(path).apply { shell = operationShell }
        if (!source.isFile || !source.canRead()) fail(R.string.wear_module_file_unreadable)
        val target = File(stagingDirectory, source.name)
        SuFileInputStream.open(source).use { input -> target.outputStream().use { input.copyTo(it) } }
        target
        }
    }

    /** A staging file the app writes before [commit] moves its content to the root path. */
    fun stagingFile(name: String): File = File(stagingDirectory, "write-$name")

    suspend fun commit(staged: File, path: String) = withContext(Dispatchers.IO) {
        try {
            cli.withNewRootShell(true) {
                val target = SuFile(path).apply { shell = this@withNewRootShell }
                staged.inputStream().use { input -> SuFileOutputStream.open(target).use { input.copyTo(it) } }
            }
        } finally {
            staged.delete()
        }
    }

    suspend fun export(uri: Uri): String = withContext(Dispatchers.IO) {
        val source = bugreport.create()
        val output = application.contentResolver.openOutputStream(uri) ?: fail(R.string.operation_failed)
        output.use { target -> source.inputStream().use { it.copyTo(target) } }
        displayName(uri.toString()) ?: uri.toString()
    }

    /** The user-facing file name of a picked URI, or null when the provider does not report one. */
    suspend fun displayName(uri: String): String? = withContext(Dispatchers.IO) {
        val parsed = uri.toUri()
        runCatching {
            application.contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: parsed.lastPathSegment
    }

    /** Creates a bugreport and returns a content URI that a share target can read. */
    suspend fun shareUri(): String = withContext(Dispatchers.IO) {
        FileProvider.getUriForFile(application, "${BuildConfig.APPLICATION_ID}.fileprovider", bugreport.create()).toString()
    }
}
