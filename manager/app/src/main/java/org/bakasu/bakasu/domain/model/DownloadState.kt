package org.bakasu.bakasu.domain.model

import androidx.annotation.StringRes
import org.bakasu.bakasu.R

enum class DownloadStatus { PENDING, DOWNLOADING, COMPLETED, FAILED }

enum class DownloadVerificationFailure(@param:StringRes val resId: Int) {
    URL_NOT_ALLOWED(R.string.repo_download_url_blocked),
    SIZE_MISMATCH(R.string.repo_download_verify_failed),
    SHA256_MISMATCH(R.string.repo_download_verify_failed),
}

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
    val expected: ExpectedFile? = null,
    val verificationFailure: DownloadVerificationFailure? = null,
)
