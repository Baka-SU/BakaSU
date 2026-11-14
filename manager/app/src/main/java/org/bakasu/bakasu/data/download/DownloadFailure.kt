package org.bakasu.bakasu.data.download

import android.system.ErrnoException
import android.system.OsConstants
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.bakasu.bakasu.domain.model.DownloadFailure
import org.bakasu.bakasu.domain.model.DownloadFailureReason

internal class DownloadHttpException(val status: Int) : IOException("HTTP $status")

/** Preserve actionable causes without displaying exception strings or guessing generic I/O failures. */
internal fun downloadFailure(error: Exception): DownloadFailure {
    val storageError = generateSequence<Throwable>(error) { it.cause }.any {
        it is ErrnoException && it.errno in setOf(OsConstants.ENOSPC, OsConstants.EACCES, OsConstants.EROFS, OsConstants.EDQUOT)
    }
    return when {
        error is DownloadHttpException -> DownloadFailure(DownloadFailureReason.HTTP, error.status)
        error is SocketTimeoutException -> DownloadFailure(DownloadFailureReason.TIMEOUT)
        error is UnknownHostException || error is ConnectException -> DownloadFailure(DownloadFailureReason.NETWORK)
        error is FileNotFoundException || storageError -> DownloadFailure(DownloadFailureReason.STORAGE)
        else -> DownloadFailure(DownloadFailureReason.UNKNOWN)
    }
}
