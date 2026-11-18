package org.bakasu.bakasu.ui.webui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bakasu.bakasu.BuildConfig
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.download.DownloadService

/**
 * Bridge exposed to the WebUI as `ksu_download`.
 *
 * Saves blobs either directly (`save`, small payloads) or in chunks spooled through
 * [BlobDownloadHandler] (large payloads, served back to WebView through the internal blob host).
 */
class WebUIDownloadInterface(private val state: WebUIState) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val idCounter = AtomicInteger(0)
    private val webView get() = state.webView

    companion object {
        // Use a different notification ID range to avoid collision with DownloadService
        private const val WEBUI_NOTIFICATION_ID_BASE = 200000
        private const val TAG = "WebUIDownload"
    }

    @JavascriptInterface
    fun openExternal(url: String) {
        val currentWebView = webView ?: return
        val uri = url.toUri()
        if (uri.scheme != "http" && uri.scheme != "https") return

        runCatching {
            currentWebView.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to open $url", throwable)
        }
    }

    @JavascriptInterface
    fun save(base64: String, fileName: String?) {
        val currentWebView = webView ?: return
        val context = currentWebView.context
        val displayName = sanitizeWebUIDownloadFileName(fileName)
        val mimeType = MimeUtil.getMimeFromFileName(displayName) ?: "application/octet-stream"
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val downloadId = WEBUI_NOTIFICATION_ID_BASE + idCounter.incrementAndGet()

        ensureNotificationChannel(notificationManager, context)
        notificationManager.notify(downloadId, buildProgressNotification(context, displayName, 0))

        scope.launch {
            val decoded = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrElse {
                Log.e(TAG, "Failed to decode $displayName", it)
                notificationManager.notify(downloadId, buildFailureNotification(context, displayName))
                postToast(context.getString(R.string.download_failed_content, displayName))
                return@launch
            }

            var pendingUri: Uri? = null
            val savedUri = try {
                val published = publishToDownloads(context, displayName, mimeType, decoded) { written ->
                    val progress = if (decoded.isEmpty()) {
                        100
                    } else {
                        ((written * 100L) / decoded.size.toLong()).toInt().coerceIn(0, 100)
                    }
                    if (progress % 2 == 0 || progress == 100) {
                        notificationManager.notify(
                            downloadId,
                            buildProgressNotification(context, displayName, progress),
                        )
                    }
                }
                pendingUri = published.first
                published.second
            } catch (throwable: Throwable) {
                pendingUri?.let { uri ->
                    if (uri.scheme == "content") {
                        runCatching { context.contentResolver.delete(uri, null, null) }
                    }
                }
                Log.e(TAG, "Failed to save $displayName", throwable)
                notificationManager.notify(downloadId, buildFailureNotification(context, displayName))
                postToast(context.getString(R.string.download_failed_content, displayName))
                null
            }

            if (savedUri != null) {
                notificationManager.notify(
                    downloadId,
                    buildCompletionNotification(context, downloadId, displayName, savedUri, mimeType),
                )
                postToast(context.getString(R.string.download_complete_content, displayName))
            }
        }
    }

    @JavascriptInterface
    fun startChunkedDownload(fileName: String?, mimeType: String?): String {
        val currentWebView = webView ?: return ""
        val sanitizedFileName = sanitizeWebUIDownloadFileName(fileName)
        return runCatching {
            BlobDownloadHandler.registerBlobDownload(
                sanitizedFileName,
                mimeType,
                currentWebView.context.cacheDir,
            )
        }.getOrElse {
            Log.e(TAG, "Failed to start chunked download", it)
            ""
        }
    }

    @JavascriptInterface
    fun writeDownloadChunk(downloadId: String, base64Chunk: String): Boolean = BlobDownloadHandler.writeChunk(downloadId, base64Chunk)

    @JavascriptInterface
    fun completeChunkedDownload(downloadId: String): Boolean = BlobDownloadHandler.completeDownload(downloadId)

    @JavascriptInterface
    fun cancelChunkedDownload(downloadId: String) {
        BlobDownloadHandler.cancelDownload(downloadId)
    }

    fun destroy() {
        scope.cancel()
        BlobDownloadHandler.cleanup()
    }

    /**
     * Persist [content] into the public Downloads collection and return the publishable uri.
     *
     * Returns the pending uri (for cleanup) plus the final uri.
     */
    private fun publishToDownloads(
        context: Context,
        displayName: String,
        mimeType: String,
        content: ByteArray,
        onBytesWritten: (Long) -> Unit,
    ): Pair<Uri, Uri> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(collection, values)
                ?: error("Failed to create download")
            val output = context.contentResolver.openOutputStream(uri)
                ?: error("Failed to open download")
            output.use { stream ->
                writeWebUIDownload(ByteArrayInputStream(content), stream, onBytesWritten)
            }
            val published = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            if (context.contentResolver.update(uri, published, null, null) != 1) {
                error("Failed to publish download")
            }
            return uri to uri
        }

        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val target = resolveAvailableTarget(downloadsDir, displayName)
        writeWebUIDownload(target, ByteArrayInputStream(content), onBytesWritten = onBytesWritten)
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            target,
        )
        return uri to uri
    }

    private fun resolveAvailableTarget(directory: File, fileName: String): File {
        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val extension = if (dotIndex > 0) fileName.substring(dotIndex) else ""

        var index = 0
        while (true) {
            val candidateName = if (index == 0) fileName else "$baseName ($index)$extension"
            val candidate = File(directory, candidateName)
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun postToast(message: String) {
        webView?.let { currentWebView ->
            currentWebView.post {
                Toast.makeText(currentWebView.context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ensureNotificationChannel(notificationManager: NotificationManager, context: Context) {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                DownloadService.CHANNEL_ID,
                context.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun buildProgressNotification(
        context: Context,
        fileName: String,
        progress: Int,
    ) = NotificationCompat.Builder(context, DownloadService.CHANNEL_ID)
        .setContentTitle(context.getString(R.string.download_progress_title, fileName))
        .setContentText("$progress%")
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setProgress(100, progress, progress == 0)
        .setOngoing(true)
        .setSilent(true)
        .build()

    private fun buildCompletionNotification(
        context: Context,
        downloadId: Int,
        fileName: String,
        contentUri: Uri,
        mimeType: String,
    ): android.app.Notification {
        val openIntent = Intent.createChooser(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            context.getString(R.string.open),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            downloadId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, DownloadService.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.download_complete_title))
            .setContentText(context.getString(R.string.download_complete_content, fileName))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_view, context.getString(R.string.open), pendingIntent)
            .build()
    }

    private fun buildFailureNotification(
        context: Context,
        fileName: String,
    ) = NotificationCompat.Builder(context, DownloadService.CHANNEL_ID)
        .setContentTitle(context.getString(R.string.download_failed_title))
        .setContentText(context.getString(R.string.download_failed_content, fileName))
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setAutoCancel(true)
        .build()
}
