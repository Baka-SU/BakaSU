package org.bakasu.bakasu.ui.wear

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.file.WearFileProvider
import org.bakasu.bakasu.ui.wear.component.WearTimeText
import org.bakasu.bakasu.ui.wear.screen.file.WearFilePage

/** App-only SAF picker returning [WearFileProvider] URIs for open, get-content and create requests. */
class WearFilePickerActivity : WearActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saving = intent.action == Intent.ACTION_CREATE_DOCUMENT
        val mimeTypes = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.takeIf { it.isNotEmpty() }?.toList()
            ?: listOf(intent.type ?: "*/*")
        val name = intent.getStringExtra(Intent.EXTRA_TITLE).orEmpty()
        setContent {
            WearManagerTheme(toastState) {
                AppScaffold(
                    timeText = { WearTimeText(stringResource(R.string.wear_picker_mode)) },
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    WearFilePage(mimeTypes, saving, name, onBack = { finish() }) { uri ->
                        setResult(RESULT_OK, Intent().setData(uri))
                        finish()
                    }
                }
            }
        }
    }
}

internal val DocumentActions = setOf(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_GET_CONTENT)

internal fun Context.isSystemDocumentPickerUnavailable(intent: Intent): Boolean = intent.action in DocumentActions && packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    ?.activityInfo?.name.let {
        it == null || it.endsWith("DocumentsStub") || it == WearFilePickerActivity::class.java.name
    }

/**
 * Routes implicit SAF requests to the built-in picker when the system picker is absent or a stub.
 * [pickerMode] `builtin` forces the app picker; `system` leaves the intent unchanged.
 */
fun Context.withDocumentPickerFallback(intent: Intent, pickerMode: String?): Intent {
    if (intent.action !in DocumentActions || intent.component != null || intent.`package` != null) return intent
    val useBuiltin = when (pickerMode) {
        "builtin" -> true

        "system" -> false

        else -> packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.name.let { it == null || it.endsWith("DocumentsStub") }
    }
    return if (useBuiltin) Intent(intent).setPackage(packageName) else intent
}
