package org.bakasu.bakasu.domain.model

import java.io.Serializable
import java.net.URI

enum class DocumentNavigation { BLOCK, ANCHOR, EXTERNAL }

/** Permissions for displayed documents, independent of their HTML/Markdown format. */
sealed class DocumentPolicy : Serializable {
    /** A configured repository can explicitly retain access to its own LAN origin. */
    data class PublicHttps(val privateOrigin: String? = null) : DocumentPolicy() {
        init {
            require(privateOrigin == null || httpsUri(privateOrigin) != null)
        }
    }

    data class SameOrigin(val origin: String) : DocumentPolicy() {
        init {
            require(httpsUri(origin) != null)
        }
    }

    val escapeEmbeddedHtml: Boolean get() = this is SameOrigin

    fun allowsRequest(url: String, method: String): Boolean {
        if (method != "GET") return false
        val uri = httpsUri(url) ?: return false
        return when (this) {
            is PublicHttps -> true
            is SameOrigin -> sameOrigin(uri, httpsUri(origin)!!)
        }
    }

    fun allowsPrivateAddress(url: String): Boolean {
        val origin = when (this) {
            is PublicHttps -> privateOrigin ?: return false
            is SameOrigin -> this.origin
        }
        val uri = httpsUri(url) ?: return false
        return sameOrigin(uri, httpsUri(origin)!!)
    }

    companion object {
        private fun httpsUri(value: String): URI? = runCatching { URI(value) }.getOrNull()?.takeIf {
            it.scheme.equals("https", true) && !it.isOpaque && !it.host.isNullOrBlank() && it.rawUserInfo == null &&
                (it.port == -1 || it.port in 1..65535)
        }

        private fun sameOrigin(a: URI, b: URI): Boolean = a.scheme.equals(b.scheme, true) && a.host.equals(b.host, true) &&
            (if (a.port == -1) 443 else a.port) == (if (b.port == -1) 443 else b.port)
    }
}

fun CatalogModule.documentPolicy(): DocumentPolicy = if (registryMetadata != null || repositoryUrl == RepositorySource.KERNEL_SU_URL) {
    DocumentPolicy.SameOrigin(repositoryUrl)
} else {
    DocumentPolicy.PublicHttps(repositoryUrl)
}

fun ModuleReadme.navigation(url: String, hasGesture: Boolean, mainFrame: Boolean): DocumentNavigation {
    if (!mainFrame || !hasGesture) return DocumentNavigation.BLOCK
    val uri = runCatching { URI(url) }.getOrNull() ?: return DocumentNavigation.BLOCK
    if (uri.rawUserInfo != null) return DocumentNavigation.BLOCK
    if (uri.rawFragment != null && url.substringBefore('#') == baseUrl.substringBefore('#')) return DocumentNavigation.ANCHOR
    return when (uri.scheme?.lowercase()) {
        "https", "http" -> if (!uri.host.isNullOrBlank()) DocumentNavigation.EXTERNAL else DocumentNavigation.BLOCK
        "mailto" -> DocumentNavigation.EXTERNAL
        else -> DocumentNavigation.BLOCK
    }
}
