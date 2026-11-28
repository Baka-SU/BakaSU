package org.bakasu.bakasu.data.file

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking
import org.bakasu.bakasu.BuildConfig
import org.bakasu.bakasu.data.packageinfo.RootServiceRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Serves the files picked in the app's own document picker as `content://` URIs, so the existing
 * ContentResolver-based callers read and write them as they do with DocumentsUI results. The files
 * are opened directly through [RootServiceRepository], using root when available. Read and write
 * failures reach the caller. The provider is not exported and grants no URI permissions, so only
 * this app can use it.
 */
class WearFileProvider :
    ContentProvider(),
    KoinComponent {
    private val rootService: RootServiceRepository by inject()

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection?.filter { it == OpenableColumns.DISPLAY_NAME }?.toTypedArray()
            ?: arrayOf(OpenableColumns.DISPLAY_NAME)
        return MatrixCursor(columns).apply { addRow(columns.map { uri.lastPathSegment }) }
    }

    override fun getType(uri: Uri) = uri.lastPathSegment?.let(::wearFileMimeType)

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val path = uri.path ?: throw FileNotFoundException(uri.toString())
        return runCatching { runBlocking { rootService.openFile(path, ParcelFileDescriptor.parseMode(mode)) } }
            .getOrElse { throw FileNotFoundException(it.message) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.wearfiles"

        fun uriFor(path: String): Uri = Uri.Builder().scheme("content").authority(AUTHORITY).path(path).build()
    }
}
