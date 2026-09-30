package com.resukisu.resukisu.ui.component.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R

/** Uses the watch IME, including its voice input, without importing a phone text-field control. */
@Composable
fun WearTextInputPage(title: String, initialValue: String, onBack: () -> Unit, multiline: Boolean = false, onSubmit: (String) -> Unit) {
    var value by rememberSaveable(title, initialValue) { mutableStateOf(initialValue) }
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, null, title) }
        item {
            WearScaledItem(spec) {
                BasicTextField(
                    value = value, onValueChange = { value = it }, singleLine = !multiline,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit(value) }),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                        .padding(12.dp).semantics { contentDescription = title },
                    decorationBox = { field ->
                        if (value.isEmpty()) Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        field()
                    },
                )
            }
        }
        item { WearActionButton(spec, Icons.Default.Check, stringResource(R.string.confirm), { onSubmit(value) }) }
    }
}
