package org.bakasu.bakasu.domain.model

import androidx.annotation.StringRes
import java.io.IOException
import org.bakasu.bakasu.R

enum class RepositoryApiType(@param:StringRes val resId: Int) {
    KERNEL_SU(R.string.repo_api_kernel_su),
    MMRL(R.string.repo_api_mmrl),
}

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

enum class RepositoryError(@param:StringRes val error: Int) {
    INVALID_URL(R.string.repo_error_url),
    INVALID_FEED(R.string.repo_error_feed),
    UNSUPPORTED_FORMAT(R.string.repo_error_format),
    NETWORK(R.string.repo_error_network),
    OFFLINE(R.string.network_offline),
    STORAGE(R.string.repo_error_storage),
}

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
