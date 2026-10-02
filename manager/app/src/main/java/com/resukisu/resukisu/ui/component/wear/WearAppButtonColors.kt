package com.resukisu.resukisu.ui.component.wear

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.compose.material3.ButtonDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * Icon tints by package. List rows leave and re-enter composition while scrolling, so the icon is
 * decoded and averaged once per package instead of on every appearance.
 */
private val iconTints = LruCache<String, Color>(256)

@Composable
fun wearAppButtonColors(packageName: String, authorized: Boolean) = run {
    val context = LocalContext.current
    val tint by produceState(if (authorized) iconTints.get(packageName) else null, packageName, authorized) {
        if (!authorized || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = context.packageManager.getApplicationIcon(packageName).toBitmap(48, 48)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                var red = 0L; var green = 0L; var blue = 0L; var count = 0
                for (pixel in pixels) {
                    if (android.graphics.Color.alpha(pixel) >= 128) {
                        red += android.graphics.Color.red(pixel)
                        green += android.graphics.Color.green(pixel)
                        blue += android.graphics.Color.blue(pixel)
                        count++
                    }
                }
                // A dark mask keeps white text readable regardless of the source icon's luminance.
                if (count == 0) null else Color(red / count / 255f * .35f,
                    green / count / 255f * .35f, blue / count / 255f * .35f)
            }.getOrNull()?.also { iconTints.put(packageName, it) }
        }
    }
    val iconTint = tint
    when {
        // Unauthorized apps keep the neutral tonal surface.
        !authorized -> ButtonDefaults.filledTonalButtonColors()
        // Until the icon color is read, an authorized app uses the paired primary container roles.
        iconTint == null -> ButtonDefaults.filledVariantButtonColors()
        else -> ButtonDefaults.buttonColors(
            containerColor = iconTint, contentColor = Color.White,
            secondaryContentColor = Color.White, iconColor = Color.White,
        )
    }
}
