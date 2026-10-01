package com.resukisu.resukisu.data.file

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.logging.BugreportRepository
import com.resukisu.resukisu.data.shell.KsuCliRepository
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What a picker selects: module or AnyKernel3 ZIPs, images, a save directory, boot images or LKM `.ko` files. */
enum class WearFileMode { ZIP, IMAGE, DIRECTORY, BOOT_IMAGE, KERNEL_MODULE, JSON, JSON_DIRECTORY, TEXT }
data class WearFileEntry(val path: String, val name: String, val directory: Boolean, val size: Long)
data class WearDirectory(val path: String, val parent: String?, val entries: List<WearFileEntry>, val writable: Boolean)

/** A failure whose message is already a localized, user-facing resource string. */
class WearFileException(message: String) : Exception(message)

class WearFileRepository(
    private val application: Application,
    private val cli: KsuCliRepository,
    private val bugreport: BugreportRepository,
) {
    private fun fail(resource: Int): Nothing = throw WearFileException(application.getString(resource))

    /** Stub DocumentsUI activities on some watches resolve the intent but cannot pick files. */
    suspend fun hasSystemPicker(mode: WearFileMode): Boolean = withContext(Dispatchers.IO) {
        val intent = when (mode) {
            WearFileMode.DIRECTORY -> Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/gzip")
            WearFileMode.JSON_DIRECTORY -> Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
            WearFileMode.JSON -> Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json")
            WearFileMode.TEXT -> Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
            WearFileMode.IMAGE -> Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
            WearFileMode.ZIP, WearFileMode.BOOT_IMAGE -> Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/octet-stream"))
            WearFileMode.KERNEL_MODULE -> Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/octet-stream")
        }.addCategory(Intent.CATEGORY_OPENABLE)
        application.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .any { !it.activityInfo.name.endsWith("DocumentsStub") }
    }

    suspend fun list(path: String?, mode: WearFileMode): WearDirectory = withContext(Dispatchers.IO) {
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
            child.canRead() && (child.isDirectory || when (mode) {
                WearFileMode.ZIP -> child.extension.equals("zip", true)
                WearFileMode.IMAGE -> child.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
                WearFileMode.BOOT_IMAGE -> child.extension.lowercase() in setOf("img", "zip")
                WearFileMode.KERNEL_MODULE -> child.extension.equals("ko", true)
                WearFileMode.JSON -> child.extension.equals("json", true)
                WearFileMode.TEXT -> true
                WearFileMode.DIRECTORY, WearFileMode.JSON_DIRECTORY -> false
            })
        }.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .map { WearFileEntry(it.path, it.name, it.isDirectory, if (it.isDirectory) 0 else it.length()) }, directory.canWrite())
        }
    }

    /** Root paths must be staged before the existing ContentResolver-based installation can read them. */
    suspend fun prepare(path: String): String = withContext(Dispatchers.IO) {
        cli.withNewRootShell(true) {
        val operationShell = this
        val source = SuFile(path).apply { shell = operationShell }
        if (!source.isFile || !source.canRead()) fail(R.string.wear_module_file_unreadable)
        val targetDirectory = File(application.cacheDir, "wear-picker").apply { mkdirs() }
        val target = File(targetDirectory, source.name)
        SuFileInputStream.open(source).use { input -> target.outputStream().use { input.copyTo(it) } }
        Uri.fromFile(target).toString()
        }
    }

    suspend fun export(directory: String, name: String): String = withContext(Dispatchers.IO) {
        if (name.isBlank() || name == "." || name == ".." || '/' in name || '\\' in name) fail(R.string.operation_failed)
        val source = bugreport.create()
        cli.withNewRootShell(true) {
        val operationShell = this
        val target = SuFile(File(directory, name).path).apply { shell = operationShell }
        if (target.exists()) fail(R.string.wear_file_exists)
        source.inputStream().use { input -> SuFileOutputStream.open(target).use { input.copyTo(it) } }
        target.path
        }
    }

    suspend fun export(uri: Uri): String = withContext(Dispatchers.IO) {
        val source = bugreport.create()
        val output = application.contentResolver.openOutputStream(uri) ?: fail(R.string.operation_failed)
        output.use { target -> source.inputStream().use { it.copyTo(target) } }
        uri.toString()
    }

    /** The user-facing file name of a picked URI, or null when the provider does not report one. */
    suspend fun displayName(uri: String): String? = withContext(Dispatchers.IO) {
        val parsed = uri.toUri()
        if (parsed.scheme == "file") return@withContext parsed.lastPathSegment
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
