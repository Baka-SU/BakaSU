package org.bakasu.bakasu.ui.screen.main

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Undo
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.DeleteForever
import androidx.compose.ui.graphics.vector.ImageVector
import org.bakasu.bakasu.R

enum class UninstallType(@param:StringRes val title: Int, @param:StringRes val message: Int, val icon: ImageVector) {
    TEMPORARY(
        R.string.settings_uninstall_temporary,
        R.string.settings_uninstall_temporary_message,
        Icons.TwoTone.Delete,
    ),
    PERMANENT(
        R.string.settings_uninstall_permanent,
        R.string.settings_uninstall_permanent_message,
        Icons.TwoTone.DeleteForever,
    ),
    RESTORE_STOCK_IMAGE(
        R.string.settings_restore_stock_image,
        R.string.settings_restore_stock_image_message,
        Icons.AutoMirrored.TwoTone.Undo,
    ),
    NONE(0, 0, Icons.TwoTone.Delete),
}
