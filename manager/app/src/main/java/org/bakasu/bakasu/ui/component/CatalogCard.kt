package org.bakasu.bakasu.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.ui.theme.CardConfig
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.theme.renderBackgroundBlur
import org.bakasu.bakasu.ui.viewmodel.formatFileSize
import org.koin.compose.koinInject

@Composable
fun CatalogCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val themeConfig = koinInject<ThemeConfig>()
    val cardConfig = koinInject<CardConfig>()
    Surface(
        color = if (themeConfig.isEnableBlurExp) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceBright.copy(cardConfig.cardAlpha)
        },
        modifier = modifier.clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .renderBackgroundBlur(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp), content = content)
    }
}

@Composable
fun CatalogCardHeading(title: String, scrollTitle: Boolean = true, trailingContent: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).then(if (scrollTitle) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailingContent()
    }
}

@Composable
fun moduleAssetDetails(asset: ModuleReleaseAsset, format: Int = R.string.assert_support_content): String? {
    val size = asset.size?.let(::formatFileSize)
    return when {
        size != null && asset.downloadCount != null -> stringResource(format, size, asset.downloadCount)
        size != null -> size
        asset.downloadCount != null -> stringResource(R.string.repo_download_count, asset.downloadCount)
        else -> null
    }
}
