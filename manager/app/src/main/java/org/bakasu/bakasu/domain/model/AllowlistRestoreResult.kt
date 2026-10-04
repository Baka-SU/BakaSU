package org.bakasu.bakasu.domain.model

sealed interface AllowlistRestoreResult {
    data object Success : AllowlistRestoreResult
    data object InvalidFile : AllowlistRestoreResult
    data object UnsupportedVersion : AllowlistRestoreResult
    data class ProfileUpdateFailed(val uid: Int) : AllowlistRestoreResult
    data object Failed : AllowlistRestoreResult
}
