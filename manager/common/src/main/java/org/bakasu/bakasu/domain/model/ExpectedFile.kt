package org.bakasu.bakasu.domain.model

/** Expected properties of a registry release ZIP. */
data class ExpectedFile(val size: Long, val sha256: String) {
    /** Same expectation regardless of hash spelling. */
    fun sameAs(other: ExpectedFile?): Boolean = other != null && size == other.size && sha256.equals(other.sha256, ignoreCase = true)
}
