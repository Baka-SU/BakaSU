package com.resukisu.resukisu.ui.component.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun wearAppButtonColors(packageName: String, authorized: Boolean) = run {
    val context = LocalContext.current
    val tint by produceState<Color?>(null, packageName, authorized) {
        value = if (!authorized) null else withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = context.packageManager.getApplicationIcon(packageName).toBitmap(48, 48)
                var red = 0L; var green = 0L; var blue = 0L; var count = 0
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
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
            }.getOrNull()
        }
    }
    ButtonDefaults.buttonColors(
        containerColor = if (authorized) tint ?: MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (authorized) Color.White else MaterialTheme.colorScheme.onSurface,
        secondaryContentColor = if (authorized) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor = if (authorized) Color.White else MaterialTheme.colorScheme.onSurface,
    )
}
