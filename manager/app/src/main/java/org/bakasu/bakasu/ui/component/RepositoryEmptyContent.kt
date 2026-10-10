package org.bakasu.bakasu.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.RepositoryError
import org.bakasu.bakasu.domain.model.RepositoryFailure

@Composable
fun repositoryErrorText(error: RepositoryError): String = stringResource(
    when (error) {
        RepositoryError.INVALID_URL -> R.string.repo_error_url
        RepositoryError.INVALID_FEED -> R.string.repo_error_feed
        RepositoryError.UNSUPPORTED_FORMAT -> R.string.repo_error_format
        RepositoryError.NETWORK -> R.string.repo_error_network
        RepositoryError.OFFLINE -> R.string.network_offline
        RepositoryError.STORAGE -> R.string.repo_error_storage
    },
)

@Composable
fun repositoryErrorText(failure: RepositoryFailure): String {
    val reason = repositoryErrorText(failure.reason)
    return failure.httpStatus?.let { stringResource(R.string.repo_error_http, reason, it) } ?: reason
}

@Composable
fun RepositoryEmptyContent(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(
            Icons.TwoTone.Extension,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}
