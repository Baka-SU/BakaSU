package org.bakasu.bakasu.domain.model

enum class DownloadStatus { PENDING, DOWNLOADING, COMPLETED, FAILED }

enum class DownloadVerificationFailure { URL_NOT_ALLOWED, SIZE_MISMATCH, SHA256_MISMATCH }

data class DownloadState(
    val id: Int,
    val fileName: String,
    val url: String,
    val progress: Int = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val resultUri: String? = null,
    val error: String? = null,
    val expected: ExpectedFile? = null,
    val verificationFailure: DownloadVerificationFailure? = null,
)
