package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.RepositoryError
import org.bakasu.bakasu.domain.model.RepositoryFailure

/** Persistent failure content, matching the warning, title and reason in the Wear demo. */
@Composable
internal fun WearLoadFailureScreen(reason: String, onBack: () -> Unit, onRetry: () -> Unit) {
    WearList(onBack = onBack, onRefresh = onRetry) { spec ->
        item {
            WearScaledItem(spec) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.Warning, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.wear_load_failed), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
        item { WearActionButton(spec, Icons.Rounded.Refresh, stringResource(R.string.network_retry), onRetry) }
    }
}

@Composable
internal fun wearRepositoryFailureText(failure: RepositoryFailure?): String {
    val message = stringResource(
        when (failure?.reason) {
            RepositoryError.OFFLINE -> R.string.wear_no_internet
            RepositoryError.NETWORK, null -> R.string.wear_repo_unreachable
            else -> failure.reason.error
        },
    )
    return failure?.httpStatus?.let { stringResource(R.string.repo_error_http, message, it) } ?: message
}
