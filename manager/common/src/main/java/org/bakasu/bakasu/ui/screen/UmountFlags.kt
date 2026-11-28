package org.bakasu.bakasu.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.bakasu.bakasu.R

@Composable
fun Int.toUmountFlagName(): String = when (this) {
    -1 -> stringResource(R.string.unknown)
    0 -> "UMOUNT_UNUSED"
    1 -> "MNT_FORCE"
    2 -> "MNT_DETACH"
    4 -> "MNT_EXPIRE"
    8 -> "UMOUNT_NOFOLLOW"
    else -> toString()
}
