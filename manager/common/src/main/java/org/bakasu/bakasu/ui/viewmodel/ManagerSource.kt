package org.bakasu.bakasu.ui.viewmodel

import androidx.annotation.StringRes
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.DynamicManagerApp

/** The fixed manager's source is the kernel-reported signature slot on both form factors. */
@StringRes
fun DynamicManagerApp.managerSourceLabel(): Int? = if (isChangeable) {
    null
} else {
    when (managerSignatureIndex) {
        254 -> R.string.dynamic_manager_source_debug
        253 -> R.string.dynamic_manager_source_toolkit
        else -> R.string.dynamic_manager_source_kernel
    }
}
