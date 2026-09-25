package com.resukisu.resukisu.ui.component.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text

@Composable
fun WearSettingsSwitchWidget(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SwitchButton(
        modifier = Modifier.fillMaxWidth(),
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        label = { Text(label) },
    )
}
