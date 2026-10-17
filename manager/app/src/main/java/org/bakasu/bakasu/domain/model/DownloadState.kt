package org.bakasu.bakasu.domain.model

enum class DownloadStatus { PENDING, DOWNLOADING, COMPLETED, FAILED }

data class DownloadState(
    val id: Int,
    val fileName: String,
    val url: String,
    val progress: Int = 0,
    /** Bytes written so far, and the size the server announced, or -1 when it did not. */
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val resultUri: String? = null,
    val error: String? = null,
)
