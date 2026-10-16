package org.bakasu.bakasu.ui.component

import androidx.compose.runtime.compositionLocalOf

/** Applies to app swipe detection only; system back remains available. */
val LocalWearSwipeEnabled = compositionLocalOf { true }
