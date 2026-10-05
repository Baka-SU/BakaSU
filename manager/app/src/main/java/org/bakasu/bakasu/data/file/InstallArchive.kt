package org.bakasu.bakasu.data.file

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileNotFoundException
import org.apache.commons.compress.archivers.zip.ZipFile

/** Reads selected entries without decompressing unrelated files in the installation ZIP. */
internal fun <T> withInstallArchive(
    context: Context,
    uri: Uri,
    read: (ZipFile) -> T,
): T {
    val archive = File.createTempFile("install-archive-", ".zip", context.cacheDir)
    try {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException(uri.toString())
        input.use { source ->
            archive.outputStream().use { target -> source.copyTo(target) }
        }
        return ZipFile.builder()
            .setFile(archive)
            .setIgnoreLocalFileHeader(true)
            .get()
            .use(read)
    } finally {
        archive.delete()
    }
}
