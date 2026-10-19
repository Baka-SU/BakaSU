package org.bakasu.bakasu.ui.component

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Help
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.GetApp
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.InputStream
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.file.ZipFileInfo
import org.bakasu.bakasu.data.file.ZipType

@Composable
fun InstallConfirmationDialog(
    show: Boolean,
    zipFiles: List<ZipFileInfo>,
    onConfirm: (List<ZipFileInfo>) -> Unit,
    onDismiss: () -> Unit,
) {
    if (show && zipFiles.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = if (zipFiles.any { it.type == ZipType.KERNEL }) {
                            Icons.TwoTone.Memory
                        } else {
                            Icons.TwoTone.Extension
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (zipFiles.size == 1) {
                            stringResource(R.string.confirm_installation)
                        } else {
                            stringResource(R.string.confirm_multiple_installation, zipFiles.size)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(zipFiles.size) { index ->
                        val zipFile = zipFiles[index]
                        InstallItemCard(zipFile = zipFile)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onConfirm(zipFiles) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.GetApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.install_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(android.R.string.cancel),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            modifier = Modifier.widthIn(min = 320.dp, max = 560.dp),
        )
    }
}

@Composable
fun InstallItemCard(zipFile: ZipFileInfo) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when (zipFile.type) {
                ZipType.MODULE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ZipType.KERNEL -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceBright
            },
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = when (zipFile.type) {
                        ZipType.MODULE -> Icons.TwoTone.Extension
                        ZipType.KERNEL -> Icons.TwoTone.Memory
                        else -> Icons.AutoMirrored.TwoTone.Help
                    },
                    contentDescription = null,
                    tint = when (zipFile.type) {
                        ZipType.MODULE -> MaterialTheme.colorScheme.primary
                        ZipType.KERNEL -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = zipFile.name.ifEmpty {
                            when (zipFile.type) {
                                ZipType.MODULE -> stringResource(R.string.unknown_module)
                                ZipType.KERNEL -> stringResource(R.string.unknown_kernel)
                                else -> stringResource(R.string.unknown_file)
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when (zipFile.type) {
                            ZipType.MODULE -> stringResource(R.string.module_package)
                            ZipType.KERNEL -> stringResource(R.string.kernel_package)
                            else -> stringResource(R.string.unknown_package)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 详细信息
            if (zipFile.version.isNotEmpty() || zipFile.author.isNotEmpty() ||
                zipFile.description.isNotEmpty() || zipFile.supported.isNotEmpty()
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    thickness = 0.5.dp,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 版本信息
                if (zipFile.version.isNotEmpty()) {
                    InfoRow(
                        label = stringResource(R.string.version),
                        value = zipFile.version + if (zipFile.versionCode.isNotEmpty()) " (${zipFile.versionCode})" else "",
                    )
                }

                // 作者信息
                if (zipFile.author.isNotEmpty()) {
                    InfoRow(
                        label = stringResource(R.string.author),
                        value = zipFile.author,
                    )
                }

                // 描述信息 (仅模块)
                if (zipFile.description.isNotEmpty() && zipFile.type == ZipType.MODULE) {
                    InfoRow(
                        label = stringResource(R.string.description),
                        value = zipFile.description,
                    )
                }

                // 支持设备 (仅内核)
                if (zipFile.supported.isNotEmpty() && zipFile.type == ZipType.KERNEL) {
                    InfoRow(
                        label = stringResource(R.string.supported_devices),
                        value = zipFile.supported,
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 60.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
