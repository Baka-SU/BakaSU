package com.resukisu.resukisu.ui.component.wear

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ButtonDefaults

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * Authorized apps use the high-emphasis primary roles, so every granted app shares the system
 * accent; unauthorized apps keep the neutral tonal surface.
 */
@Composable
fun wearAppButtonColors(authorized: Boolean) =
    if (authorized) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
