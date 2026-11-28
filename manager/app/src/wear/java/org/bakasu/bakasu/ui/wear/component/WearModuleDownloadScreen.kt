package org.bakasu.bakasu.ui.wear.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.DownloadVerificationFailure
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadPhase
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadUiState
import org.bakasu.bakasu.ui.viewmodel.formatFileSize

/**
 * Download identity stays fixed; progress and compact failure feedback share the remaining space.
 */
@Composable
internal fun WearModuleDownloadScreen(name: String, state: ModuleDownloadUiState, fraction: () -> Float, iconUrl: String? = null, iconOrigin: String? = null) {
    val failed = state.phase == ModuleDownloadPhase.FAILED || state.phase == ModuleDownloadPhase.VERIFY_FAILED
    val reduceMotion = LocalReduceMotion.current
    val failedProgress = remember { Animatable(0f) }
    LaunchedEffect(failed, reduceMotion) {
        if (failed) {
            failedProgress.snapTo(fraction())
            failedProgress.animateTo(1f, if (reduceMotion) snap() else tween(200))
        } else {
            failedProgress.snapTo(0f)
        }
    }
    val failureTitle = stringResource(R.string.download_failed_title)
    val failureText = moduleDownloadFailureText(state)
    Box(Modifier.fillMaxSize()) {
        ScreenScaffold(contentPadding = PaddingValues(0.dp), timeText = { Box(Modifier.testTag("module-download-clock")) { WearTimeText(forceTime = true) } }, overscrollEffect = null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val nameStyle = MaterialTheme.typography.titleMedium
                val statusStyle = MaterialTheme.typography.bodyMedium
                val percentStyle = MaterialTheme.typography.titleMedium
                val textMeasurer = rememberTextMeasurer()
                val textWidth = with(density) { (maxWidth * 0.72f).roundToPx() }
                val constraints = Constraints(maxWidth = textWidth)
                val downloading = stringResource(R.string.wear_downloading)
                val progressText = state.fraction?.let { stringResource(R.string.wear_download_percent, (it * 100).toInt()) }
                    ?: formatFileSize(state.downloadedBytes)
                // Measure the actual fonts (including fallback glyphs and bold) rather than treating
                // the declared line heights as hard bounds. Both outcomes retain one shared slot.
                val statusPixels = textMeasurer.measure(downloading, statusStyle, constraints = constraints).size.height
                val percentPixels = textMeasurer.measure(
                    progressText,
                    percentStyle.copy(fontWeight = FontWeight.Bold),
                    constraints = constraints,
                ).size.height
                val failurePixels = textMeasurer.measure(
                    failureText,
                    statusStyle,
                    constraints = constraints,
                    maxLines = 2,
                ).size.height
                val statusHeight = with(density) { maxOf(statusPixels + percentPixels + 2.dp.roundToPx(), failurePixels).toDp() }
                val nameHeight = textMeasurer.measure(name, nameStyle.copy(fontWeight = FontWeight.Bold), maxLines = 2, constraints = constraints).size.height
                val textHeight = with(density) { nameHeight.toDp() } + statusHeight + 12.dp
                val iconSize = minOf(maxWidth * 0.32f, (maxHeight * 0.78f - textHeight).coerceAtLeast(32.dp))
                Column(
                    Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = maxWidth * 0.14f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.testTag("module-download-icon")) {
                        WearCatalogModuleIcon(iconUrl, iconOrigin, iconSize)
                    }
                    Text(name, modifier = Modifier.fillMaxWidth(), style = nameStyle, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.fillMaxWidth().height(statusHeight), contentAlignment = Alignment.Center) {
                        if (failed) {
                            Text(
                                failureText,
                                style = statusStyle,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    downloading,
                                    style = statusStyle,
                                    maxLines = 1,
                                )
                                Text(
                                    progressText,
                                    style = percentStyle,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
        WearEdgeProgressIndicator(
            progress = { if (failed) failedProgress.value else fraction() },
            modifier = Modifier.testTag("module-download-ring").semantics {
                if (failed) {
                    contentDescription = failureTitle
                } else {
                    progressBarRangeInfo = state.fraction?.let { ProgressBarRangeInfo(it, 0f..1f) } ?: ProgressBarRangeInfo.Indeterminate
                }
            },
            indicatorColor = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun moduleDownloadFailureText(state: ModuleDownloadUiState): String {
    val verification = state.verificationFailure
    return when {
        verification == DownloadVerificationFailure.URL_NOT_ALLOWED -> stringResource(R.string.wear_download_url_blocked)
        verification != null -> stringResource(R.string.wear_download_verify_failed)
        state.phase == ModuleDownloadPhase.VERIFY_FAILED -> stringResource(R.string.wear_download_verify_failed)
        else -> stringResource(R.string.download_failed_title)
    }
}
