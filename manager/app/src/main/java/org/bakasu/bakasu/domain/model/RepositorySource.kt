package org.bakasu.bakasu.domain.model

data class RepositorySource(
    val url: String,
    val name: String,
    val id: String = "",
    val description: String = "",
    val customName: String = "",
) {
    val displayName: String get() = customName.ifBlank { name }
    val isBuiltIn: Boolean get() = url == KERNEL_SU_URL

    companion object {
        const val KERNEL_SU_URL = "https://modules.kernelsu.org/"
    }
}

enum class RepositoryError { INVALID_URL, INVALID_FEED, UNSUPPORTED_FORMAT, NETWORK, OFFLINE, STORAGE }

data class RepositoryFailure(val reason: RepositoryError, val httpStatus: Int? = null) {
    companion object {
        fun from(error: Throwable): RepositoryFailure = RepositoryFailure(
            reason = (error as? RepositoryException)?.reason ?: RepositoryError.NETWORK,
            httpStatus = if (error is IllegalStateException) {
                Regex("HTTP ([1-5][0-9]{2})").matchEntire(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
            } else {
                null
            },
        )
    }
}

class RepositoryException(val reason: RepositoryError) : Exception(reason.name)
