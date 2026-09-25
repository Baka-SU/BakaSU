package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearScaledItem

@Composable
fun WearManagerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    MaterialTheme(colorScheme = dynamicColorScheme(context) ?: ColorScheme()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            content()
        }
    }
}

@Composable
fun WearStartupStatus(error: String? = null) {
    AppScaffold {
        WearList { spec ->
            if (error == null) item { WearScaledItem(spec) { CircularProgressIndicator() } }
            item { WearScaledItem(spec) { Text(error ?: stringResource(R.string.wear_loading)) } }
        }
    }
}
