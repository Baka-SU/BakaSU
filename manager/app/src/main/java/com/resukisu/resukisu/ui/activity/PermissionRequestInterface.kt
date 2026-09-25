package com.resukisu.resukisu.ui.activity

import androidx.activity.compose.ManagedActivityResultLauncher

/**
 * @author AlexLiuDev233
 */
interface PermissionRequestInterface {
    /**
     * Requests an android permission.
     *
     * @param permission the permission should be request
     * @param callback   callback when request is finished, true = success, false = failed
     * @param requestDescription when android require provide description, what description should provide to user?
     * @see ManagedActivityResultLauncher
     */
    fun requestPermission(
        permission: String,
        callback: (Boolean) -> Unit,
        requestDescription: String
    )

    /**
     * Requests multiple android permission.
     *
     * @param permissions the permissions should be request
     * @param callback   callback when request is finished, true = success, false = failed
     * @param requestDescription when android require provide description, what description should provide to user?
     * @see ManagedActivityResultLauncher
     */
    fun requestPermissions(
        permissions: Array<String>,
        callback: (Map<String, @JvmSuppressWildcards Boolean>) -> Unit,
        requestDescription: Map<String, String>
    )
}