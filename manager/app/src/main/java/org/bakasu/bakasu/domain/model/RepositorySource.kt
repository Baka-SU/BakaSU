package org.bakasu.bakasu.domain.model

import java.io.IOException

enum class RepositoryApiType { KERNEL_SU, MMRL }

data class RepositorySource(
    val url: String,
    val name: String,
    val id: String = "",
    val description: String = "",
    val customName: String = "",
    val moduleCount: Int? = null,
    val apiType: RepositoryApiType = if (url == KERNEL_SU_URL) RepositoryApiType.KERNEL_SU else RepositoryApiType.MMRL,
) {
    val displayName: String get() = customName.ifBlank { name }
    val isBuiltIn: Boolean get() = url == KERNEL_SU_URL

    companion object {
        const val KERNEL_SU_URL = "https://modules.kernelsu.org/"
    }
}

enum class RepositoryError { INVALID_URL, INVALID_FEED, UNSUPPORTED_FORMAT, NETWORK, OFFLINE, STORAGE }

class HttpStatusException(val statusCode: Int) : IOException("HTTP $statusCode")

data class RepositoryFailure(val reason: RepositoryError, val httpStatus: Int? = null) {
    companion object {
        fun from(error: Throwable): RepositoryFailure = RepositoryFailure(
            reason = (error as? RepositoryException)?.reason ?: RepositoryError.NETWORK,
            httpStatus = (error as? HttpStatusException)?.statusCode,
        )
    }
}

class RepositoryException(val reason: RepositoryError) : Exception(reason.name)
