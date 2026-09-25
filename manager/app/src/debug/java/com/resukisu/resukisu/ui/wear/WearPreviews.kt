package com.resukisu.resukisu.ui.wear

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Tag
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.wear.compose.material3.AppScaffold
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.HomeDashboardState
import com.resukisu.resukisu.domain.model.HomeSystemInfo
import com.resukisu.resukisu.ui.component.wear.WearDetailField
import com.resukisu.resukisu.ui.component.wear.WearInfoCard
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearSettingsSwitchWidget
import com.resukisu.resukisu.ui.viewmodel.ModuleUiState

@Preview(
    name = "Round",
    device = "spec:width=192dp,height=192dp,dpi=320,isRound=true",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Square",
    device = "spec:width=180dp,height=180dp,dpi=320,isRound=false",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Large font",
    device = "spec:width=192dp,height=192dp,dpi=320,isRound=true",
    fontScale = 1.3f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
private annotation class WearScreenPreviews

@WearScreenPreviews
@Composable
private fun WearHomePreview() {
    WearManagerTheme {
        AppScaffold {
            WearHomePage(
                state = HomeDashboardState(
                    systemInfo = HomeSystemInfo(
                        deviceModel = stringResource(R.string.home_device_model),
                        androidVersion = android.os.Build.VERSION.RELEASE,
                    ),
                    isInitialDataLoaded = true,
                ),
                error = null,
            )
        }
    }
}

@WearScreenPreviews
@Composable
private fun WearDetailsAndSettingsPreview() {
    WearManagerTheme {
        AppScaffold {
            WearList { spec ->
                item { WearPageHeader(spec, Icons.TwoTone.Info, stringResource(R.string.about)) }
                item {
                    WearInfoCard(spec, Modifier.fillMaxWidth()) {
                        WearDetailField(
                            Icons.TwoTone.Tag,
                            stringResource(R.string.app_name),
                            BuildConfig.VERSION_NAME,
                        )
                    }
                }
                item {
                    WearSettingsSwitchWidget(
                        spec,
                        label = stringResource(R.string.wear_manager_updates),
                        checked = true,
                        onCheckedChange = {},
                    )
                }
            }
        }
    }
}

enum class WearPreviewLoadState { EMPTY, LOADING, ERROR }

class WearLoadStateProvider : PreviewParameterProvider<WearPreviewLoadState> {
    override val values = WearPreviewLoadState.entries.asSequence()
}

@WearScreenPreviews
@Composable
private fun WearModuleStatesPreview(
    @PreviewParameter(WearLoadStateProvider::class) loadState: WearPreviewLoadState,
) {
    WearManagerTheme {
        AppScaffold {
            WearModulesPage(
                state = ModuleUiState(isLoading = loadState == WearPreviewLoadState.LOADING),
                error = if (loadState == WearPreviewLoadState.ERROR) {
                    stringResource(R.string.operation_failed)
                } else null,
                onRefresh = {},
                onModuleClick = {},
            )
        }
    }
}
