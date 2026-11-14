package org.bakasu.bakasu.data.download

import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.bakasu.bakasu.domain.model.DownloadFailureReason
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFailureTest {
    @Test
    fun preservesHttpStatusAndDistinguishesActionableFailures() {
        assertEquals(503, downloadFailure(DownloadHttpException(503)).httpStatus)
        assertEquals(DownloadFailureReason.HTTP, downloadFailure(DownloadHttpException(404)).reason)
        assertEquals(DownloadFailureReason.TIMEOUT, downloadFailure(SocketTimeoutException()).reason)
        assertEquals(DownloadFailureReason.NETWORK, downloadFailure(UnknownHostException()).reason)
        assertEquals(DownloadFailureReason.NETWORK, downloadFailure(ConnectException()).reason)
        assertEquals(DownloadFailureReason.STORAGE, downloadFailure(FileNotFoundException()).reason)
        assertEquals(DownloadFailureReason.UNKNOWN, downloadFailure(IOException("Unclassified I/O")).reason)
    }
}
