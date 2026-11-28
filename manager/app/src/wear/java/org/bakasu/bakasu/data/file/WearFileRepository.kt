package org.bakasu.bakasu.data.file

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.io.SuFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.shell.KsuCliRepository
import org.bakasu.bakasu.ui.webui.MimeUtil

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
) {
    private fun fail(resource: Int): Nothing = throw WearFileException(application.getString(resource))

    /**
     * Whether browsing shared storage still needs a permission: without root, plain file access
     * sees only media and the app's own files until all-files access (API 30+) or the read
     * permission (older releases) is granted.
     */
    private fun needsStoragePermission(): Boolean {
        // All-files access exists for watches without DocumentsUI; a phone uses its own picker.
        if (!application.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return !Environment.isExternalStorageManager()
        }
        return ContextCompat.checkSelfPermission(application, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
    }

    /** Reports storage access before listing [path], so permission guidance survives a listing failure. */
    suspend fun list(
        path: String?,
        mimeTypes: List<String>,
        directoriesOnly: Boolean,
        onStoragePermissionChecked: (Boolean) -> Unit,
    ): WearDirectory = withContext(Dispatchers.IO) {
        cli.withNewRootShell(true) {
            onStoragePermissionChecked(!isRoot && needsStoragePermission())
            val operationShell = this
            fun file(path: String): File = if (isRoot) SuFile(path).apply { shell = operationShell } else File(path)
            val directory = path?.let(::file) ?: listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path,
                Environment.getExternalStorageDirectory().path,
                application.getExternalFilesDir(null)?.path,
                application.filesDir.path,
            ).filterNotNull().map(::file).firstOrNull { it.isDirectory && it.canRead() }
                ?: fail(R.string.wear_directory_unavailable)
            val children = directory.listFiles() ?: fail(R.string.wear_directory_unavailable)
            WearDirectory(
                directory.path,
                directory.parent,
                children.filter { child ->
                    child.canRead() && (
                        child.isDirectory ||
                            !directoriesOnly && matchesMimeType(wearFileMimeType(child.name), mimeTypes)
                        )
                }.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                    .map { WearFileEntry(it.path, it.name, it.isDirectory, if (it.isDirectory) 0 else it.length()) },
                directory.canWrite(),
            )
        }
    }

    /** The path of a new file [name] in [directory], checked to be a plain name that does not exist yet. */
    suspend fun newFile(directory: String, name: String): String = withContext(Dispatchers.IO) {
        if (name.isBlank() || name == "." || name == ".." || '/' in name || '\\' in name) fail(R.string.operation_failed)
        cli.withNewRootShell(true) {
            val target = if (isRoot) {
                SuFile(File(directory, name).path).apply { shell = this@withNewRootShell }
            } else {
                File(directory, name)
            }
            if (target.exists()) fail(R.string.wear_file_exists)
            target.path
        }
    }
}
